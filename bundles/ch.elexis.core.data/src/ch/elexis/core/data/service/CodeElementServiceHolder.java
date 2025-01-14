package ch.elexis.core.data.service;

import java.util.HashMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.model.IPersistentObject;
import ch.elexis.core.services.ICodeElementService;
import ch.elexis.core.services.ICodeElementService.ContextKeys;
import ch.elexis.data.Fall;
import ch.elexis.data.Konsultation;

@Component(service = {})
public class CodeElementServiceHolder {
	
	private static HashMap<Object, Object> emptyMap = new HashMap<>();
	
	private static ICodeElementService elementService;
	
	@Reference(unbind = "-")
	public void setCodeElementService(ICodeElementService elementService){
		CodeElementServiceHolder.elementService = elementService;
	}
	
	public static ICodeElementService getService(){
		return elementService;
	}
	
	/**
	 * Create a context map using the selection of {@link ElexisEventDispatcher}.
	 * 
	 * @return
	 */
	public static HashMap<Object, Object> createContext(){
		HashMap<Object, Object> ret = new HashMap<>();
		IPersistentObject consultation = ElexisEventDispatcher.getSelected(Konsultation.class);
		if (consultation != null) {
			ret.put(ContextKeys.CONSULTATION, consultation);
			ret.put(ContextKeys.COVERAGE, ((Konsultation) consultation).getFall());
		}
		if (ret.get(ContextKeys.COVERAGE) == null) {
			IPersistentObject coverage = ElexisEventDispatcher.getSelected(Fall.class);
			if (coverage != null) {
				ret.put(ContextKeys.COVERAGE, coverage);
			}
		}
		return ret;
	}
	
	/**
	 * Create a context map using the provided {@link Konsultation}.
	 * 
	 * @param consultation
	 * @return
	 */
	public static HashMap<Object, Object> createContext(Konsultation consultation){
		HashMap<Object, Object> ret = new HashMap<>();
		if (consultation != null) {
			ret.put(ContextKeys.CONSULTATION, consultation);
		}
		if (consultation != null) {
			ret.put(ContextKeys.COVERAGE, consultation.getFall());
		}
		return ret;
	}
	
	public static HashMap<Object, Object> emtpyContext(){
		return emptyMap;
	}
}
