/*******************************************************************************
 * Copyright (c) 2006-2010, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 * 
 *******************************************************************************/

package ch.elexis.core.ui.views;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.statushandlers.StatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.core.constants.Preferences;
import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEvent;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.data.events.ElexisEventListener;
import ch.elexis.core.data.interfaces.IVerrechenbar;
import ch.elexis.core.data.status.ElexisStatus;
import ch.elexis.core.l10n.Messages;
import ch.elexis.core.model.ICodeElement;
import ch.elexis.core.model.IDiagnose;
import ch.elexis.core.model.prescription.EntryType;
import ch.elexis.core.ui.Hub;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.actions.CodeSelectorHandler;
import ch.elexis.core.ui.events.ElexisUiEventListenerImpl;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.locks.AcquireLockUi;
import ch.elexis.core.ui.locks.IUnlockable;
import ch.elexis.core.ui.locks.LockDeniedNoActionLockHandler;
import ch.elexis.core.ui.util.PersistentObjectDropTarget;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.views.codesystems.LeistungenView;
import ch.elexis.core.ui.views.controls.InteractionLink;
import ch.elexis.data.Artikel;
import ch.elexis.data.Eigenleistung;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Leistungsblock;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Prescription;
import ch.elexis.data.Verrechnet;
import ch.rgw.tools.Money;
import ch.rgw.tools.Result;
import ch.rgw.tools.StringTool;

public class VerrechnungsDisplay extends Composite implements IUnlockable {
	
	private Text billedLabel;
	private InteractionLink interactionLink;
	private Table table;
	private TableViewer viewer;
	private MenuManager contextMenuManager;
	private Konsultation actEncounter;
	private String defaultRGB;
	private IWorkbenchPage page;
	private final PersistentObjectDropTarget dropTarget;
	private IAction applyMedicationAction, chPriceAction, chCountAction, chTextAction, removeAction,
			removeAllAction;
	
	private static final String INDICATED_MEDICATION =
		Messages.VerrechnungsDisplay_indicatedMedication;
	private static final String APPLY_MEDICATION = Messages.VerrechnungsDisplay_applyMedication;
	private static final String CHPRICE = Messages.VerrechnungsDisplay_changePrice;
	private static final String CHCOUNT = Messages.VerrechnungsDisplay_changeNumber;
	private static final String REMOVE = Messages.VerrechnungsDisplay_removeElements;
	private static final String CHTEXT = Messages.VerrechnungsDisplay_changeText;
	private static final String REMOVEALL = Messages.VerrechnungsDisplay_removeAll;
	static Logger logger = LoggerFactory.getLogger(VerrechnungsDisplay.class);
	
	private final ElexisEventListener eeli_update =
		new ElexisUiEventListenerImpl(Konsultation.class, ElexisEvent.EVENT_UPDATE) {
			@Override
			public void runInUi(ElexisEvent ev){
				PersistentObject obj = ev.getObject();
				if (obj != null && obj.equals(actEncounter)) {
					viewer.setInput(actEncounter.getLeistungen());
				}
			}
		};
	private TableColumnLayout tableLayout;
	private ToolBarManager toolBarManager;
	private TableViewerColumn partDisposalColumn;
	
	public VerrechnungsDisplay(final IWorkbenchPage p, Composite parent, int style){
		super(parent, style);
		final int columns_for_each_drug = 4;
		setLayout(new GridLayout(columns_for_each_drug, false));
		this.page = p;
		defaultRGB = UiDesk.createColor(new RGB(255, 255, 255));
		
		Label label = new Label(this, SWT.NONE);
		FontDescriptor boldDescriptor =
			FontDescriptor.createFrom(label.getFont()).setStyle(SWT.BOLD);
		Font boldFont = boldDescriptor.createFont(label.getDisplay());
		label.setFont(boldFont);
		label.setText(Messages.VerrechnungsDisplay_billing);
		
		billedLabel = new Text(this, SWT.WRAP);
		billedLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		interactionLink = new InteractionLink(this, SWT.NONE);
		interactionLink.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		toolBarManager = new ToolBarManager(SWT.RIGHT);
		IAction newAction = new Action() {

			@Override
			public ImageDescriptor getImageDescriptor(){
				return Images.IMG_NEW.getImageDescriptor();
			}
			
			@Override
			public void run(){
				try {
					if (StringTool.isNothing(LeistungenView.ID)) {
						SWTHelper.alert(Messages.VerrechnungsDisplay_error, "LeistungenView.ID"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					page.showView(LeistungenView.ID);
					CodeSelectorHandler.getInstance().setCodeSelectorTarget(dropTarget);
				} catch (Exception ex) {
					ElexisStatus status =
						new ElexisStatus(ElexisStatus.ERROR, Hub.PLUGIN_ID, ElexisStatus.CODE_NONE,
							Messages.VerrechnungsDisplay_errorStartingCodeWindow + ex.getMessage(),
							ex, ElexisStatus.LOG_ERRORS);
					StatusManager.getManager().handle(status, StatusManager.SHOW);
				}
			}
			
			@Override
			public boolean isEnabled(){
				return actEncounter != null && actEncounter.isBillable();
			}
		};
		newAction.setToolTipText(Messages.VerrechnungsDisplay_AddItem);
		toolBarManager.add(newAction);

		toolBarManager.add(new Action("", Action.AS_CHECK_BOX) {
			
			@Override
			public ImageDescriptor getImageDescriptor(){
				return Images.IMG_NOBILLING.getImageDescriptor();
			}
			
			@Override
			public String getText(){
				return Messages.VerrechnungsDisplay_no_invoice;
			}
			
			@Override
			public void run(){
				actEncounter.setBillable(!actEncounter.isBillable());
				updateUi();
			}
			
			@Override
			public boolean isChecked(){
				if (actEncounter != null) {
					return !actEncounter.isBillable();
				}
				return false;
			}
			
			@Override
			public boolean isEnabled(){
				return actEncounter != null && !isBilled(actEncounter);
			}
			
			private boolean isBilled(Konsultation encounter){
				if (encounter != null) {
					return encounter.getRechnung() != null;
				}
				return false;
			}
		});
		ToolBar toolBar = toolBarManager.createControl(this);
		toolBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		
		makeActions();
		tableLayout = new TableColumnLayout();
		Composite tableComposite = new Composite(this, SWT.NONE);

		tableComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, columns_for_each_drug, 1));
		tableComposite.setLayout(tableLayout);
		viewer = new TableViewer(tableComposite,
			SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		table = viewer.getTable();
		table.setMenu(createVerrMenu());
		// dummy table viewer needed for SelectionsProvider for Menu
		viewer.setContentProvider(ArrayContentProvider.getInstance());
		
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		createColumns();
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event){
				IStructuredSelection selection = viewer.getStructuredSelection();
				if (selection != null && !selection.isEmpty()
					&& (selection.getFirstElement() instanceof Verrechnet)) {
					ElexisEventDispatcher
						.fireSelectionEvent((PersistentObject) selection.getFirstElement());
				}
			}
		});
		table.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e){}
			
			@Override
			public void keyPressed(KeyEvent e){
				if (e.keyCode == SWT.DEL) {
					if (table.getSelectionIndices().length >= 1 && removeAction != null) {
						removeAction.run();
					}
				}
			}
		});
		// connect double click on column to actions
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e){
				int clickedIndex = -1;
				// calculate column of click
				int width = 0;
				TableColumn[] columns = table.getColumns();
				for (int i = 0; i < columns.length; i++) {
					TableColumn tc = columns[i];
					if (width < e.x && e.x < width + tc.getWidth()) {
						clickedIndex = i;
						break;
					}
					width += tc.getWidth();
				}
				if(clickedIndex != -1) {
					if (clickedIndex == 1) {
						chCountAction.run();
					} else if (clickedIndex == 4) {
						chPriceAction.run();
					} else if (clickedIndex == 5) {
						removeAction.run();
					}
				}
			}
		});
		
		dropTarget = new PersistentObjectDropTarget(Messages.VerrechnungsDisplay_doBill, table,
			new DropReceiver()) {
			@Override
			protected Control getHighLightControl(){
				return VerrechnungsDisplay.this;
			}
		}; //$NON-NLS-1$
		
		// refresh the table if a update to a Verrechnet occurs
		ElexisEventDispatcher.getInstance().addListeners(
			new ElexisUiEventListenerImpl(Verrechnet.class, ElexisEvent.EVENT_UPDATE) {
				@Override
				public void runInUi(ElexisEvent ev){
					PersistentObject object = ev.getObject();
					if (object != null) {
						viewer.update(object, null);
					}
				}
			});
		ElexisEventDispatcher.getInstance().addListeners(eeli_update);
	}
	
	private void createColumns(){
		String[] titles = {
				StringTool.leer,
				Messages.Display_Column_Number,
				Messages.Display_Column_Code,
				Messages.Display_Column_Designation,
				Messages.Display_Column_Price,
				StringTool.leer
		};
		int[] weights = {
			0, 8, 20, 50, 15, 7
		};
		
		partDisposalColumn = createTableViewerColumn(titles[0], weights[0], 0, SWT.LEFT);
		partDisposalColumn.setLabelProvider(new ColumnLabelProvider() {
			
			@Override
			public String getText(Object element){
				return "";
			}
			
			@Override
			public Image getImage(Object element){
				if (element instanceof Verrechnet) {
					Verrechnet billed = (Verrechnet) element;
					if (isPartDisposal(billed)) {
						return Images.IMG_BLOCKS_SMALL.getImage();
					}
				}
				return super.getImage(element);
			}
		});
		
		TableViewerColumn col = createTableViewerColumn(titles[1], weights[1], 1, SWT.LEFT);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element){
				if (element instanceof Verrechnet) {
					Verrechnet billed = (Verrechnet) element;
					return Integer.toString(billed.getZahl());
				}
				return "";
			}
		});
		
		col = createTableViewerColumn(titles[2], weights[2], 2, SWT.NONE);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element){
				if (element instanceof Verrechnet) {
					Verrechnet billed = (Verrechnet) element;
					return getServiceCode(billed);
				}
				return "";
			}
		});
		
		col = createTableViewerColumn(titles[3], weights[3], 3, SWT.NONE);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element){
				if (element instanceof Verrechnet) {
					Verrechnet billed = (Verrechnet) element;
					return billed.getText();
				}
				return "";
			}
			
			@Override
			public Color getBackground(final Object element){
				System.out.println("Getting bg of " + element);
				if (element instanceof Verrechnet) {
					Verrechnet billed = (Verrechnet) element;
					return getBackgroundColor(billed);
				}
				return null;
			}
		});
		
		col = createTableViewerColumn(titles[4], weights[4], 4, SWT.RIGHT);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element){
				if (element instanceof Verrechnet) {
					Verrechnet billed = (Verrechnet) element;
					Money price = billed.getNettoPreis().multiply(billed.getZahl());
					return price.getAmountAsString();
				}
				return "";
			}
		});
		
		col = createTableViewerColumn(titles[5], weights[5], 5, SWT.NONE);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element){
				return "";
			}
			
			@Override
			public Image getImage(Object element){
				return Images.IMG_DELETE.getImage();
			}
		});
	}
	
	private TableViewerColumn createTableViewerColumn(String title, int weight, int colNumber,
		int style){
		final TableViewerColumn viewerColumn = new TableViewerColumn(viewer, style);
		final TableColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setResizable(true);
		column.setMoveable(false);
		tableLayout.setColumnData(column, new ColumnWeightData(weight));
		return viewerColumn;
	}
	
	private Color getBackgroundColor(Verrechnet billed){
		IVerrechenbar billable = billed.getVerrechenbar();
		if (billable != null) {
			Color color = UiDesk.getColorFromRGB(defaultRGB);
			String codeName = billable.getCodeSystemName();
			
			if (codeName != null) {
				String rgbColor =
					CoreHub.globalCfg.get(Preferences.LEISTUNGSCODES_COLOR + codeName, defaultRGB);
				color = UiDesk.getColorFromRGB(rgbColor);
			}
			return color;
		}
		return null;
	}
	
	public void clear(){
		actEncounter = null;
		viewer.setInput(Collections.emptyList());
		updateBilledLabel();
	}
	
	private void updateBilledLabel(){
		ArrayList<Artikel> gtins = new ArrayList<Artikel>();
		if (actEncounter != null) {
			Money sum = new Money(0);
			for (Verrechnet billed : actEncounter.getLeistungen()) {
				Money preis = billed.getNettoPreis().multiply(billed.getZahl());
				sum.addMoney(preis);
				IVerrechenbar verrechenbar = billed.getVerrechenbar();
				if (verrechenbar != null && verrechenbar instanceof Artikel) {
					Artikel art = (Artikel) verrechenbar;
					gtins.add(art);
				}
			}
			interactionLink.updateAtcs(gtins);
			billedLabel.setText(String.format("%s %s / %s %s", //$NON-NLS-1$
				Messages.VerrechnungsDisplay_Amount, sum.getAmountAsString(),
				Messages.VerrechnungsDisplay_Time, actEncounter.getMinutes()));
		} else {
			billedLabel.setText(""); //$NON-NLS-1$
		}
		layout();
	}
	
	public void addPersistentObject(PersistentObject o){
		Konsultation actKons = (Konsultation) ElexisEventDispatcher.getSelected(Konsultation.class);
		if (actKons != null) {
			if (o instanceof Leistungsblock) {
				Leistungsblock block = (Leistungsblock) o;
				List<ICodeElement> elements = block.getElements();
				for (ICodeElement element : elements) {
					if (element instanceof PersistentObject) {
						addPersistentObject((PersistentObject) element);
					}
				}
				List<ICodeElement> diff = block.getDiffToReferences(elements);
				if (!diff.isEmpty()) {
					StringBuilder sb = new StringBuilder();
					diff.forEach(r -> {
						if (sb.length() > 0) {
							sb.append("\n");
						}
						sb.append(r);
					});
					MessageDialog.openWarning(getShell(), "Warnung",
						"Warnung folgende Leistungen konnten im aktuellen Kontext (Fall, Konsultation, Gesetz) nicht verrechnet werden.\n"
							+ sb.toString());
				}
			}
			if (o instanceof Prescription) {
				Prescription presc = (Prescription) o;
				o = presc.getArtikel();
			}
			if (o instanceof IVerrechenbar) {
				if (CoreHub.acl.request(AccessControlDefaults.LSTG_VERRECHNEN) == false) {
					SWTHelper.alert(Messages.VerrechnungsDisplay_missingRightsCaption, //$NON-NLS-1$
						Messages.VerrechnungsDisplay_missingRightsBody); //$NON-NLS-1$
				} else {
					Result<IVerrechenbar> result = actKons.addLeistung((IVerrechenbar) o);
					
					if (!result.isOK()) {
						SWTHelper.alert(Messages.VerrechnungsDisplay_imvalidBilling,
							result.toString()); //$NON-NLS-1$
					}
					viewer.setInput(actKons.getLeistungen());
					updatePartDisposalColumn(actKons.getLeistungen());
				}
			} else if (o instanceof IDiagnose) {
				actKons.addDiagnose((IDiagnose) o);
			}
		}
	}
	
	private void updatePartDisposalColumn(List<Verrechnet> list){
		boolean hasDisposal = false;
		for (Verrechnet billed : list) {
			if (isPartDisposal(billed)) {
				hasDisposal = true;
				break;
			}
		}
		if (hasDisposal) {
			partDisposalColumn.getColumn().setWidth(18);
		} else {
			partDisposalColumn.getColumn().setWidth(0);
		}
	}
	
	private boolean isPartDisposal(Verrechnet billed){
		IVerrechenbar billable = billed.getVerrechenbar();
		if (billable instanceof Artikel) {
			Artikel a = (Artikel) billable;
			int abgabeEinheit = a.getAbgabeEinheit();
			if (abgabeEinheit > 0 && abgabeEinheit < Math.abs(a.getPackungsGroesse())) {
				return true;
			}
		}
		return false;
	}
	
	private final class DropReceiver implements PersistentObjectDropTarget.IReceiver {
		public void dropped(PersistentObject o, DropTargetEvent ev){
			if (accept(o)) {
				addPersistentObject(o);
			}
		}
		
		public boolean accept(PersistentObject o){
			if (ElexisEventDispatcher.getSelectedPatient() != null) {
				if (o instanceof Artikel) {
					return !((Artikel) o).isProduct();
				}
				if (o instanceof IVerrechenbar) {
					return true;
				}
				if (o instanceof IDiagnose) {
					return true;
				}
				if (o instanceof Leistungsblock) {
					return true;
				}
				if (o instanceof Prescription) {
					Prescription p = ((Prescription) o);
					return (p.getArtikel() != null && !p.getArtikel().isProduct());
				}
			}
			return false;
		}
	}
	
	public void setEncounter(Konsultation encounter){
		actEncounter = encounter;
		viewer.setInput(encounter.getLeistungen());
		updatePartDisposalColumn(encounter.getLeistungen());
		updateBilledLabel();
		updateUi();
	}
	
	private void updateUi(){
		if (toolBarManager != null) {
			for (IContributionItem contribution : toolBarManager.getItems()) {
				contribution.update();
			}
			toolBarManager.update(true);
		}
		viewer.getTable().setEnabled(actEncounter != null && actEncounter.isBillable());
	}
	
	/**
	 * Filter codes of {@link Verrechnet} where ID is used as code. This is relevant for
	 * {@link Eigenleistung} and Eigenartikel.
	 * 
	 * @param lst
	 * @return
	 */
	private String getServiceCode(Verrechnet verrechnet){
		String ret = verrechnet.getCode();
		IVerrechenbar verrechenbar = verrechnet.getVerrechenbar();
		if (verrechenbar != null) {
			if (verrechenbar instanceof Eigenleistung || (verrechenbar instanceof Artikel
				&& "Eigenartikel".equals(((Artikel) verrechenbar).get(Artikel.FLD_TYP)))) {
				if (verrechenbar.getId().equals(ret)) {
					ret = "";
				}
			}
		}
		return ret;
	}
	
	private Menu createVerrMenu(){
		contextMenuManager = new MenuManager();
		contextMenuManager.setRemoveAllWhenShown(true);
		contextMenuManager.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager){
				IStructuredSelection selection = viewer.getStructuredSelection();
				if (selection.size() > 1) {
					manager.add(removeAction);
				} else {
					if (!selection.isEmpty()) {
						Verrechnet v = (Verrechnet) selection.getFirstElement();
						IVerrechenbar verrechenbar = v.getVerrechenbar();
						
						manager.add(chPriceAction);
						manager.add(chCountAction);
						IVerrechenbar vbar = v.getVerrechenbar();
						List<IAction> itemActions = (List<IAction>) (List<?>) vbar.getActions(v);
						if ((itemActions != null) && (itemActions.size() > 0)) {
							manager.add(new Separator());
							for (IAction a : itemActions) {
								if (a != null) {
									manager.add(a);
								}
							}
						}
						manager.add(new Separator());
						manager.add(chTextAction);
						manager.add(removeAction);
						manager.add(new Separator());
						manager.add(removeAllAction);
						if (verrechenbar instanceof Artikel) {
							manager.add(new Separator());
							manager.add(applyMedicationAction);
							// #8796
							manager.add(new Action(INDICATED_MEDICATION, Action.AS_CHECK_BOX) {
								@Override
								public void run(){
									IStructuredSelection selection =
										viewer.getStructuredSelection();
									for (Object selected : selection.toList()) {
										if (selected instanceof Verrechnet) {
											Verrechnet billed = (Verrechnet) selected;
											AcquireLockUi.aquireAndRun(billed,
												new LockDeniedNoActionLockHandler() {
													
													@Override
													public void lockAcquired(){
														if (isIndicated(billed)) {
															billed.setDetail(Verrechnet.INDICATED,
																"false");
														} else {
															billed.setDetail(Verrechnet.INDICATED,
																"true");
														}
													}
												});
										}
									}
								}
								
								private boolean isIndicated(Verrechnet billed){
									String value = billed.getDetail(Verrechnet.INDICATED);
									return "true".equalsIgnoreCase(value);
								}
								
								@Override
								public boolean isChecked(){
									return isIndicated(v);
								}
							});
						}
					}
				}
			}
		});
		return contextMenuManager.createContextMenu(table);
	}
	
	private void makeActions(){
		// #3278
		applyMedicationAction = new Action(APPLY_MEDICATION) {
			@Override
			public void run(){
				IStructuredSelection selection = viewer.getStructuredSelection();
				for (Object selected : selection.toList()) {
					if (selected instanceof Verrechnet) {
						Verrechnet billed = (Verrechnet) selected;
						AcquireLockUi.aquireAndRun(billed, new LockDeniedNoActionLockHandler() {
							@Override
							public void lockAcquired(){
								billed.setDetail(Verrechnet.VATSCALE, Double.toString(0.0));
								
								int packungsGroesse =
									((Artikel) billed.getVerrechenbar()).getPackungsGroesse();
								String proposal =
									(packungsGroesse > 0) ? "1/" + packungsGroesse : "1";
								changeQuantityDialog(proposal, billed);
								Object prescriptionId =
									billed.getDetail(Verrechnet.FLD_EXT_PRESC_ID);
								if (prescriptionId instanceof String) {
									Prescription prescription =
										Prescription.load((String) prescriptionId);
									if (prescription.getEntryType() == EntryType.SELF_DISPENSED) {
										prescription.setApplied(true);
									}
								}
							}
						});
					}
				}
			}
			
			@Override
			public ImageDescriptor getImageDescriptor(){
				return Images.IMG_SYRINGE.getImageDescriptor();
			}
		};
		
		removeAction = new Action(REMOVE) {
			@Override
			public void run(){
				IStructuredSelection selection = viewer.getStructuredSelection();
				for (Object selected : selection.toList()) {
					if (selected instanceof Verrechnet) {
						Verrechnet billed = (Verrechnet) selected;
						AcquireLockUi.aquireAndRun(billed, new LockDeniedNoActionLockHandler() {
							@Override
							public void lockAcquired(){
								Result<Verrechnet> result = ((Konsultation) ElexisEventDispatcher
									.getSelected(Konsultation.class)).removeLeistung(billed);
								if (!result.isOK()) {
									SWTHelper.alert(
										Messages.VerrechnungsDisplay_PositionCanootBeRemoved, result //$NON-NLS-1$
											.toString());
								}
							}
							
						});
					}
				}
				setEncounter(actEncounter);
			}
		};
		
		removeAllAction = new Action(REMOVEALL) {
			@Override
			public void run(){
				if (!actEncounter.isEditable(true)) {
					return;
				}
				List<Verrechnet> allBilled = actEncounter.getLeistungen();
				for (Verrechnet billed : allBilled) {
					AcquireLockUi.aquireAndRun(billed, new LockDeniedNoActionLockHandler() {
						@Override
						public void lockAcquired(){
							Result<Verrechnet> result = ((Konsultation) ElexisEventDispatcher
								.getSelected(Konsultation.class)).removeLeistung(billed);
							if (!result.isOK()) {
								SWTHelper.alert(
									Messages.VerrechnungsDisplay_PositionCanootBeRemoved, result //$NON-NLS-1$
										.toString());
							}
						}
					});
				}
				setEncounter(actEncounter);
			}
		};
		
		chPriceAction = new Action(CHPRICE) {
			
			@Override
			public void run(){
				if (!actEncounter.isEditable(true)) {
					return;
				}
				
				IStructuredSelection selection = viewer.getStructuredSelection();
				for (Object selected : selection.toList()) {
					if (selected instanceof Verrechnet) {
						Verrechnet billed = (Verrechnet) selected;
						AcquireLockUi.aquireAndRun(billed, new LockDeniedNoActionLockHandler() {
							
							@Override
							public void lockAcquired(){
								Money oldPrice = billed.getBruttoPreis();
								String p = oldPrice.getAmountAsString();
								InputDialog dlg = new InputDialog(UiDesk.getTopShell(),
									Messages.VerrechnungsDisplay_changePriceForService, //$NON-NLS-1$
									Messages.VerrechnungsDisplay_enterNewPrice, p, //$NON-NLS-1$
									null);
								if (dlg.open() == Dialog.OK) {
									try {
										String val = dlg.getValue().trim();
										Money newPrice = new Money(oldPrice);
										if (val.endsWith("%") && val.length() > 1) { //$NON-NLS-1$
											val = val.substring(0, val.length() - 1);
											double percent = Double.parseDouble(val);
											double factor = 1.0 + (percent / 100.0);
											billed.setSecondaryScaleFactor(factor);
										} else {
											newPrice = new Money(val);
											billed.setTP(newPrice.getCents());
											billed.setSecondaryScaleFactor(1);
											// mark as changed price
											billed.setDetail(Verrechnet.FLD_EXT_CHANGEDPRICE,
												"true");
										}
										viewer.update(billed, null);
									} catch (ParseException ex) {
										SWTHelper.showError(
											Messages.VerrechnungsDisplay_badAmountCaption, //$NON-NLS-1$
											Messages.VerrechnungsDisplay_badAmountBody); //$NON-NLS-1$
									}
								}
							}
						});
					}
				}
				updateBilledLabel();
			}
		};
		
		chCountAction = new Action(CHCOUNT) {
			@Override
			public void run(){
				if (!actEncounter.isEditable(true)) {
					return;
				}
				
				IStructuredSelection selection = viewer.getStructuredSelection();
				for (Object selected : selection.toList()) {
					if (selected instanceof Verrechnet) {
						Verrechnet billed = (Verrechnet) selected;
						String p = Integer.toString(billed.getZahl());
						AcquireLockUi.aquireAndRun(billed, new LockDeniedNoActionLockHandler() {
							
							@Override
							public void lockAcquired(){
								changeQuantityDialog(p, billed);
							}
						});
					}
				}
				updateBilledLabel();
			}
		};
		
		chTextAction = new Action(CHTEXT) {
			@Override
			public void run(){
				if (!actEncounter.isEditable(true)) {
					return;
				}
				
				IStructuredSelection selection = viewer.getStructuredSelection();
				for (Object selected : selection.toList()) {
					if (selected instanceof Verrechnet) {
						Verrechnet billed = (Verrechnet) selected;
						AcquireLockUi.aquireAndRun(billed, new LockDeniedNoActionLockHandler() {
							@Override
							public void lockAcquired(){
								String oldText = billed.getText();
								InputDialog dlg = new InputDialog(UiDesk.getTopShell(),
									Messages.VerrechnungsDisplay_changeTextCaption, //$NON-NLS-1$
									Messages.VerrechnungsDisplay_changeTextBody, //$NON-NLS-1$
									oldText, null);
								if (dlg.open() == Dialog.OK) {
									String input = dlg.getValue();
									if (input.matches("[0-9\\.,]+")) { //$NON-NLS-1$
										if (!SWTHelper.askYesNo(
											Messages.VerrechnungsDisplay_confirmChangeTextCaption, //$NON-NLS-1$
											Messages.VerrechnungsDisplay_confirmChangeTextBody)) { //$NON-NLS-1$
											return;
										}
									}
									billed.setText(input);
									viewer.update(billed, null);
								}
							}
						});
					}
				}
			}
		};
	}
	
	private void changeQuantityDialog(String p, Verrechnet v){
		InputDialog dlg =
			new InputDialog(UiDesk.getTopShell(), Messages.VerrechnungsDisplay_changeNumberCaption, //$NON-NLS-1$
				Messages.VerrechnungsDisplay_changeNumberBody, //$NON-NLS-1$
				p, null);
		if (dlg.open() == Dialog.OK) {
			try {
				String val = dlg.getValue();
				if (!StringTool.isNothing(val)) {
					int changeAnzahl;
					double secondaryScaleFactor = 1.0;
					String text = v.getVerrechenbar().getText();
					
					if (val.indexOf(StringConstants.SLASH) > 0) {
						changeAnzahl = 1;
						String[] frac = val.split(StringConstants.SLASH);
						secondaryScaleFactor =
							Double.parseDouble(frac[0]) / Double.parseDouble(frac[1]);
						text = v.getText() + " (" + val //$NON-NLS-1$
							+ Messages.VerrechnungsDisplay_Orininalpackungen;
					} else if (val.indexOf('.') > 0) {
						changeAnzahl = 1;
						secondaryScaleFactor = Double.parseDouble(val);
						text = v.getText() + " (" + Double.toString(secondaryScaleFactor) + ")";
					} else {
						changeAnzahl = Integer.parseInt(dlg.getValue());
					}
					
					IStatus ret = v.changeAnzahlValidated(changeAnzahl);
					if (ret.isOK()) {
						v.setSecondaryScaleFactor(secondaryScaleFactor);
						v.setText(text);
					} else {
						SWTHelper.showError(Messages.VerrechnungsDisplay_error, ret.getMessage());
					}
				}
				viewer.update(v, null);
			} catch (NumberFormatException ne) {
				SWTHelper.showError(Messages.VerrechnungsDisplay_invalidEntryCaption, //$NON-NLS-1$
					Messages.VerrechnungsDisplay_invalidEntryBody); //$NON-NLS-1$
			}
		}
	}
	
	@Override
	public void setUnlocked(boolean unlocked){
		setEnabled(unlocked);
		redraw();
	}
	
	public MenuManager getMenuManager(){
		return contextMenuManager;
	}
	
	public StructuredViewer getViewer(){
		return viewer;
	}
	
	public void adaptMenus(){
		table.getMenu().setEnabled(CoreHub.acl.request(AccessControlDefaults.LSTG_VERRECHNEN));
	}
}
