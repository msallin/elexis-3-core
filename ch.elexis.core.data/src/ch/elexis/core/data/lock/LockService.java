package ch.elexis.core.data.lock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eclipsesource.jaxrs.consumer.ConsumerFactory;

import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.constants.ElexisSystemPropertyConstants;
import ch.elexis.core.data.events.ElexisEvent;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.data.events.ElexisEventListener;
import ch.elexis.core.data.events.ElexisEventListenerImpl;
import ch.elexis.core.data.status.ElexisStatus;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.User;
import info.elexis.server.elexis.common.jaxrs.ILockService;
import info.elexis.server.elexis.common.types.LockInfo;
import info.elexis.server.elexis.common.types.LockRequest;
import info.elexis.server.elexis.common.types.LockResponse;

/**
 * DO NOT SUPPORT LOCKING FOR MORE THAN ONE OBJECT AT ONCE!!
 * 
 * @author marco
 *
 */
public class LockService {

	private static ILockService ils;

	private HashMap<String, LockInfo> locks = new HashMap<String, LockInfo>();
	private final boolean standalone;
	private Logger log = LoggerFactory.getLogger(LockService.class);

	/**
	 * A unique id for this instance of Elexis. Changes on every restart
	 */
	private static final UUID systemUuid = UUID.randomUUID();

	/**
	 * storage for current patient, to unlock after patient switch
	 */
	private Patient oldPatient = null;
	
	private ElexisEventListener eeli_pat = new ElexisEventListenerImpl(Patient.class, ElexisEvent.EVENT_SELECTED) {
		public void run(ElexisEvent ev) {
			if (ev.getObject() == null) {
				return;
			}
			if(oldPatient instanceof Patient) {
				String sts = oldPatient.storeToString();
				if (sts != null) {
					if (ownsLock(sts)) {
						releaseLock(sts);
					}
				}
			}
			oldPatient = (Patient) ev.getObject();
		};
	};

	public LockService(BundleContext context) {
		final String restUrl = System.getProperty(ElexisSystemPropertyConstants.ELEXIS_SERVER_REST_INTERFACE_URL);
		if (restUrl != null) {
			standalone = false;
			log.info("Operating against elexis-server instance on " + restUrl);
			ils = ConsumerFactory.createConsumer(restUrl, ILockService.class);
		} else {
			standalone = true;
			log.info("Operating in stand-alone mode.");
		}
		ElexisEventDispatcher.getInstance().addListeners(eeli_pat);
	}

	public static String getSystemuuid() {
		return systemUuid.toString();
	}

	public LockResponse acquireLock(PersistentObject po) {
		if (po == null) {
			return LockResponse.DENIED(null);
		}
		return acquireLock(po.storeToString());
	}

	public LockResponse acquireLock(String storeToString) {
		if (storeToString == null) {
			return LockResponse.DENIED(null);
		}

		User user = (User) ElexisEventDispatcher.getSelected(User.class);
		LockInfo lockInfo = new LockInfo(storeToString, user.getId(), systemUuid.toString());
		LockRequest lockRequest = new LockRequest(LockRequest.Type.ACQUIRE, lockInfo);
		return acquireOrReleaseLock(lockRequest);
	}

	/**
	 * 
	 * @param storeToString
	 *            if <code>null</code> returns false
	 * @return
	 */
	public boolean ownsLock(String storeToString) {
		if (storeToString == null) {
			return false;
		}

		if (standalone) {
			return true;
		}

		String elementId = LockInfo.getElementId(storeToString);
		return locks.containsKey(elementId);
	}

	public LockResponse releaseLock(String storeToString) {
		User user = (User) ElexisEventDispatcher.getSelected(User.class);
		LockInfo lil = LockStrategy.createLockInfoList(storeToString, user.getId(), systemUuid.toString());
		LockRequest lockRequest = new LockRequest(LockRequest.Type.RELEASE, lil);
		return acquireOrReleaseLock(lockRequest);
	}

	public LockResponse releaseAllLocks() {
		if (standalone) {
			return LockResponse.OK;
		}

		List<LockInfo> lockList = new ArrayList<LockInfo>(locks.values());
		for (LockInfo lockInfo : lockList) {
			LockRequest lockRequest = new LockRequest(LockRequest.Type.RELEASE, lockInfo);
			LockResponse lr = acquireOrReleaseLock(lockRequest);
			if (!lr.isOk()) {
				return lr;
			}
		}
		return LockResponse.OK;
	}

	private LockResponse acquireOrReleaseLock(LockRequest lockRequest) {
		if (standalone) {
			return LockResponse.OK;
		}

		if (ils == null) {
			String message = "System not configured for standalone mode, and elexis-server not available!";
			log.error(message);
			ElexisEventDispatcher.fireElexisStatusEvent(
					new ElexisStatus(Status.ERROR, CoreHub.PLUGIN_ID, ElexisStatus.CODE_NONE, message, null));
			return LockResponse.ERROR;
		}

		LockInfo lockInfo = lockRequest.getLockInfo();

		synchronized (locks) {
			// does the requested lock match the cache on our side?
			if (LockRequest.Type.ACQUIRE == lockRequest.getRequestType()
					&& locks.keySet().contains(lockInfo.getElementId())) {
				return LockResponse.OK;
			}

			// TODO should we release all locks on acquiring a new one?
			// if yes, this has to be dependent upon the strategy
			try {
				LockResponse lr = ils.acquireOrReleaseLocks(lockRequest);
				if (!lr.isOk()) {
					return lr;
				}

				if (LockRequest.Type.ACQUIRE == lockRequest.getRequestType()) {
					// ACQUIRE ACTIONS
					// lock is granted only if we have non-exception on acquire
					locks.put(lockInfo.getElementId(), lockInfo);

					PersistentObject po = CoreHub.poFactory.createFromString(lockInfo.getElementStoreToString());
					ElexisEventDispatcher.getInstance()
							.fire(new ElexisEvent(po, po.getClass(), ElexisEvent.EVENT_LOCK_AQUIRED));

				}
			} catch (Exception e) {
				// if we have an exception here, our lock copies never get
				// deleted!!!
				String message = "Error trying to acquireOrReleaseLocks.";
				log.error(message);
				ElexisEventDispatcher.fireElexisStatusEvent(
						new ElexisStatus(Status.ERROR, CoreHub.PLUGIN_ID, ElexisStatus.CODE_NONE, message, e));
				return LockResponse.ERROR;
			} finally {
				if (LockRequest.Type.RELEASE.equals(lockRequest.getRequestType())) {
					// RELEASE ACTIONS
					// releases are also to be performed on occurence of an
					// exception
					locks.remove(lockInfo.getElementId());

					PersistentObject po = CoreHub.poFactory.createFromString(lockInfo.getElementStoreToString());
					ElexisEventDispatcher.getInstance()
							.fire(new ElexisEvent(po, po.getClass(), ElexisEvent.EVENT_LOCK_RELEASED));

				}
			}

			return LockResponse.OK;
		}
	}

	public LockResponse releaseLock(PersistentObject po) {
		if (po == null) {
			return LockResponse.DENIED(null);
		}
		return releaseLock(po.storeToString());
	}

	// /**
	// * Query whether the given object is currently locked by someone else
	// (that is
	// * an acquireLock would fail if we return true here)
	// * @param storeToString
	// * @return
	// */
	// public boolean isLockedBySomeoneElse(String storeToString) {
	// if(standalone) {
	// return false;
	// }
	// try {
	// return ils.isLocked(storeToString);
	// } catch (Exception e) {
	// String message = "Error trying to acquireOrReleaseLocks.";
	// log.error(message);
	// ElexisEventDispatcher.fireElexisStatusEvent(
	// new ElexisStatus(Status.ERROR, CoreHub.PLUGIN_ID, ElexisStatus.CODE_NONE,
	// message, e));
	// return true;
	// }
	// }

}