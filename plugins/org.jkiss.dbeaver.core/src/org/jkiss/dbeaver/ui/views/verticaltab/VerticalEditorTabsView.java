package org.jkiss.dbeaver.ui.views.verticaltab;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.*;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.internal.IWorkbenchThemeConstants;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.themes.ITheme;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceContainerProvider;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.ui.actions.datasource.DataSourceToolbarUtils;
import org.jkiss.dbeaver.ui.editors.DatabaseLazyEditorInput;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;
import java.util.*;
import java.util.function.Supplier;
import static org.jkiss.dbeaver.ui.actions.AbstractDataSourceHandler.getExecutionContextFromPart;

public class VerticalEditorTabsView extends ViewPart implements IPartListener, IElementUpdater {
    private static final Log log = Log.getLog(VerticalEditorTabsView.class);
    Map<TabInfo, Composite> tabComposites;
    Composite tabsContainer;
    private ButtonObj<Conn> curDatasource;
    private ButtonObj<Conn.CatalogAndSchema> curSchema;
    private TabInfo lastActiveTab;
    private ContentViewer tabsViewer;
    private IWorkbenchPage workbenchPage;

    @Override
    public void createPartControl(Composite parent) {
        workbenchPage = getSite().getPage();
        workbenchPage.addPartListener(this);
        parent.setLayout(new GridLayout(1, false));
        createControlArea(parent);
        createTabsArea(parent);
        refreshAll();
        parent.getDisplay().timerExec(300, new Runnable() {
            @Override
            public void run() {
                if (!parent.isDisposed()) {
                    refreshAll();
                }
            }
        });
    }

    private void createControlArea(Composite parent) {
        Composite controlArea = new Composite(parent, SWT.NONE);
        controlArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        controlArea.setLayout(layout);
        Button dsBut = new Button(controlArea, SWT.CHECK);
        curDatasource = new ButtonObj<>("", dsBut);
        dsBut.setText("<N/A>");
        dsBut.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        dsBut.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshAll();
            }
        });
        Button schemaBut = new Button(controlArea, SWT.CHECK);
        curSchema = new ButtonObj<>("", schemaBut);
        schemaBut.setText("<N/A>");
        schemaBut.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        schemaBut.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshAll();
            }
        });
    }

    private void createTabsArea(Composite parent) {
        ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);
        tabsContainer = new Composite(scrolledComposite, SWT.NONE);
        scrolledComposite.setContent(tabsContainer);
        GridLayout containerLayout = new GridLayout(1, false);
        containerLayout.marginWidth = 0;
        containerLayout.marginHeight = 0;
        containerLayout.verticalSpacing = 1;
        tabsContainer.setLayout(containerLayout);
        tabComposites = new HashMap<>();
        tabsViewer = new ContentViewer() {
            private List<TabInfo> currentInput;

            @Override
            protected void inputChanged(Object input, Object oldInput) {
                clearAllTabs();
                if (input instanceof List) {
                    currentInput = (List<TabInfo>) input;
                    refresh();
                }
            }

            @Override
            public void refresh() {
                if (currentInput != null) {
                    clearAllTabs();
                    Set<TabInfo> processedTabs = new HashSet<>();
                    for (TabInfo tabInfo : currentInput) {
                        if (!processedTabs.contains(tabInfo)) {
                            createTabItem(tabsContainer, tabInfo);
                            processedTabs.add(tabInfo);
                        }
                    }
                    tabsContainer.layout();
                    scrolledComposite.setMinSize(tabsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
                    scrolledComposite.setMinSize(tabsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
                }
            }

            @Override
            public Object getInput() {
                return currentInput;
            }

            @Override
            public ISelection getSelection() {
                for (Map.Entry<TabInfo, Composite> entry : tabComposites.entrySet()) {
                    if (entry.getKey().isActive) {
                        return new StructuredSelection(entry.getKey());
                    }
                }
                return StructuredSelection.EMPTY;
            }

            @Override
            public void setSelection(ISelection selection, boolean reveal) {
                if (selection instanceof IStructuredSelection) {
                    Object firstElement = ((IStructuredSelection) selection).getFirstElement();
                    if (firstElement instanceof TabInfo tabInfo) {
                        Composite tabComposite = tabComposites.get(tabInfo);
                        if (tabComposite != null) {
                            selectTab(tabComposite, tabInfo);
                        }
                    }
                }
            }

            @Override
            public Control getControl() {
                return scrolledComposite;
            }
        };
        tabsViewer.setContentProvider((IStructuredContentProvider) inputElement -> {
            if (inputElement instanceof List) {
                return ((List<?>) inputElement).toArray();
            }
            return new Object[0];
        });
        createContextMenu();
    }

    private void clearAllTabs() {
        for (Control control : tabsContainer.getChildren()) {
            if (control instanceof Composite) {
                control.dispose();
            }
        }
        tabComposites.clear();
    }

    private void createTabItem(Composite parent, TabInfo tabInfo) {
        if (tabComposites.containsKey(tabInfo)) {
            return;
        }
        Composite tabComposite = new Composite(parent, SWT.NONE);
        tabComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        tabComposite.setData("tabInfo", tabInfo);
        tabComposites.put(tabInfo, tabComposite);
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 3;
        layout.marginHeight = 1;
        layout.horizontalSpacing = 3;
        tabComposite.setLayout(layout);
        tabComposite.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    selectTab(tabComposite, tabInfo);
                }
            }
        });
        Label iconLabel = new Label(tabComposite, SWT.NONE);
        if (tabInfo.image != null) {
            iconLabel.setImage(tabInfo.image);
        }
        iconLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        iconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    selectTab(tabComposite, tabInfo);
                }
            }
        });
        Label titleLabel = new Label(tabComposite, SWT.NONE);
        titleLabel.setText(tabInfo.title);
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    selectTab(tabComposite, tabInfo);
                }
            }
        });
        Button closeButton = new Button(tabComposite, SWT.PUSH | SWT.FLAT);
        closeButton.setText("×");
        closeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        closeButton.setToolTipText("Close tab");
        closeButton.setBackground(tabComposite.getBackground());
        closeButton.addMouseTrackListener(new MouseTrackAdapter() {
            @Override
            public void mouseEnter(MouseEvent e) {
                closeButton.setBackground(closeButton.getDisplay().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
            }

            @Override
            public void mouseExit(MouseEvent e) {
                closeButton.setBackground(tabComposite.getBackground());
            }
        });
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (tabInfo.editorReference != null) {
                    workbenchPage.closeEditor(tabInfo.editorReference.getEditor(false), true);
                    tabComposites.remove(tabInfo);
                }
            }
        });
        updateTabAppearance(tabComposite, tabInfo);
    }

    private void selectTab(Composite tabComposite, TabInfo tabInfo) {
        for (Composite comp : tabComposites.values()) {
            TabInfo info = (TabInfo) comp.getData("tabInfo");
            if (info != null) {
                info.isActive = false;
                updateTabAppearance(comp, info);
            }
        }
        tabInfo.isActive = true;
        updateTabAppearance(tabComposite, tabInfo);
        lastActiveTab = tabInfo; // 更新最后一个活动标签页
        if (tabInfo.editorReference != null) {
            try {
                workbenchPage.activate(tabInfo.editorReference.getPart(true));
            } catch (Exception e) {
                System.err.println("Error activating editor: " + e.getMessage());
            }
        }
        if (tabsViewer != null) {
            tabsViewer.setSelection(new StructuredSelection(tabInfo));
        }
    }

    private void createContextMenu() {
        MenuManager menuMgr = new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(manager -> {
            IStructuredSelection selection = (IStructuredSelection) tabsViewer.getSelection();
            if (!selection.isEmpty()) {
                TabInfo tabInfo = (TabInfo) selection.getFirstElement();
                manager.add(new Action("Close") {
                    @Override
                    public void run() {
                        if (tabInfo.editorReference != null) {
                            workbenchPage.closeEditor(tabInfo.editorReference.getEditor(false), true);
                        }
                    }
                });
                manager.add(new Action("Close Others") {
                    @Override
                    public void run() {
                        closeOtherTabs(tabInfo);
                    }
                });
                manager.add(new Action("Close All") {
                    @Override
                    public void run() {
                        closeAllTabs();
                    }
                });
            }
        });
        tabsViewer.getControl().setMenu(menuMgr.createContextMenu(tabsViewer.getControl()));
        getSite().registerContextMenu(menuMgr, tabsViewer);
    }

    private void closeOtherTabs(TabInfo keepTab) {
        IEditorReference[] editorRefs = workbenchPage.getEditorReferences();
        for (IEditorReference editorRef : editorRefs) {
            if (!editorRef.equals(keepTab.editorReference)) {
                workbenchPage.closeEditor(editorRef.getEditor(false), true);
            }
        }
    }

    private void closeAllTabs() {
        workbenchPage.closeAllEditors(false);
    }

    private void refreshAll() {
        refreshAll(true, true);
    }

    private void refreshAll(boolean controlFlag, boolean tabsFlag) {
        List<TabInfo> tabs = new ArrayList<>();
        IEditorReference[] editorRefs = workbenchPage.getEditorReferences();
        IEditorPart activeEditor = workbenchPage.getActiveEditor();
        boolean filterCurrentDataSource = curDatasource.isSelected();
        boolean filterCurSchema = curSchema.isSelected();
        DBPDataSourceContainer dataSource =
                DataSourceToolbarUtils.getCurrentDataSource(workbenchPage.getWorkbenchWindow());
        Conn conn = Conn.from(dataSource, activeEditor);

        // 检查 lastActiveTab 的编辑器是否仍然打开，如果已关闭则重置
        if (lastActiveTab != null) {
            boolean editorStillOpen = false;
            for (IEditorReference ref : editorRefs) {
                if (ref.equals(lastActiveTab.editorReference)) {
                    editorStillOpen = true;
                    break;
                }
            }
            if (!editorStillOpen) {
                lastActiveTab = null; // 编辑器已关闭，重置 lastActiveTab
            }
        }

        if (controlFlag) {
            if (dataSource != null) {
                curDatasource.updateT(conn);
                curSchema.updateT(conn.catalogAndSchema);
            }
        }
        if (!tabsFlag) {
            return;
        }
        for (IEditorReference editorRef : editorRefs) {
            IEditorPart editor = editorRef.getEditor(false);
            if (editor != null) {
                Conn connB = null;
                if (editor instanceof DBPDataSourceContainerProvider dscp) {
                    connB = Conn.from(dscp.getDataSourceContainer(), editor);
                }
                if (filterCurSchema) {
                    if (!Conn.sameSchema(conn, connB)) {
                        continue;
                    }
                } else if (filterCurrentDataSource) {
                    if (!Conn.sameDatasource(conn, connB)) {
                        continue;
                    }
                }
                TabInfo tabInfo = new TabInfo();
                tabInfo.editorReference = editorRef;
                tabInfo.title = editorRef.getTitle();
                tabInfo.image = editorRef.getTitleImage();
                tabInfo.conn = connB;
                // 设置 isActive：如果活动编辑器不为 null，基于活动编辑器设置；否则基于 lastActiveTab 设置
                if (activeEditor != null) {
                    tabInfo.isActive = editorRef.equals(workbenchPage.getReference(activeEditor));
                    if (tabInfo.isActive) {
                        lastActiveTab = tabInfo; // 更新 lastActiveTab 为当前活动标签页
                    }
                } else {
                    tabInfo.isActive = (lastActiveTab != null && lastActiveTab.editorReference.equals(editorRef));
                }
                tabInfo.isPinned = editorRef.isPinned();
                tabInfo.tooltip = editorRef.getTitleToolTip();
                tabs.add(tabInfo);
            }
        }
        tabs.sort((e1, e2) -> e1.isPinned ? 1 : -(e2.isPinned ? 1 : 0));
        tabsViewer.setInput(tabs);
        tabsViewer.refresh();
        // 更新活动标签页的外观（即使活动编辑器为 null，lastActiveTab 可能已设置）
        if (activeEditor != null) {
            var activeRef = workbenchPage.getReference(activeEditor);
            for (TabInfo tab : tabs) {
                if (tab.editorReference.equals(activeRef)) {
                    Composite tabComposite = tabComposites.get(tab);
                    if (tabComposite != null) {
                        updateTabAppearance(tabComposite, tab);
                    }
                    break;
                }
            }
        } else if (lastActiveTab != null) {
            // 如果活动编辑器为 null，但 lastActiveTab 存在，确保其外观更新
            Composite tabComposite = tabComposites.get(lastActiveTab);
            if (tabComposite != null) {
                updateTabAppearance(tabComposite, lastActiveTab);
            }
        }
    }

    private void updateTabAppearance(Composite tabComposite, TabInfo tabInfo) {
        ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
        if (tabInfo.isActive) {
            Color bgColor = theme.getColorRegistry().get(IWorkbenchThemeConstants.ACTIVE_TAB_VERTICAL);
            Color fgColor = theme.getColorRegistry().get(IWorkbenchThemeConstants.ACTIVE_TAB_TEXT_COLOR);
            tabComposite.setBackground(bgColor);
            tabComposite.setBackgroundMode(SWT.INHERIT_NONE);
            for (Control child : tabComposite.getChildren()) {
                if (child instanceof Label) {
                    child.setBackground(bgColor);
                    child.setForeground(fgColor);
                    child.setToolTipText(tabInfo.tooltip);
                } else if (child instanceof Button) {
                    child.setBackground(bgColor);
                    child.setForeground(fgColor);
                }
            }
        } else {
            Color defaultBg = theme.getColorRegistry().get(IWorkbenchThemeConstants.INACTIVE_TAB_VERTICAL);
            Color defaultFg = theme.getColorRegistry().get(IWorkbenchThemeConstants.INACTIVE_TAB_TEXT_COLOR);
            tabComposite.setBackground(defaultBg);
            for (Control child : tabComposite.getChildren()) {
                if (child instanceof Label) {
                    child.setToolTipText(tabInfo.tooltip);
                    child.setBackground(defaultBg);
                    child.setForeground(defaultFg);
                } else if (child instanceof Button) {
                    child.setBackground(defaultBg);
                    child.setForeground(defaultFg);
                }
            }
        }
        tabComposite.redraw();
        tabComposite.update();
    }

    @Override
    public void setFocus() {
        tabsViewer.getControl().setFocus();
    }

    @Override
    public void dispose() {
        if (workbenchPage != null) {
            workbenchPage.removePartListener(this);
        }
        lastActiveTab = null; // 清理资源
        super.dispose();
    }

    @Override
    public void partOpened(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            refreshAll();
        }
    }

    @Override
    public void partClosed(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            refreshAll();
        }
    }

    @Override
    public void partActivated(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            refreshAll();
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPart part) {}

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {}

    @Override
    public void updateElement(UIElement element, Map parameters) {
        if ("true".equals(parameters.get("noCustomLabel"))) {
            return;
        }
        refreshAll(true, false);
    }

    private static class TabInfo {
        IEditorReference editorReference;
        String title;
        Image image;
        String tooltip;
        boolean isActive;
        boolean isPinned;
        Conn conn;
    }

    private record Conn(String id, String user, String ip, String port, CatalogAndSchema catalogAndSchema)
            implements ButtonObj.TextObj {
        private static final String NA = "<N/A>";

        private static String read(Supplier<String> read) {
            try {
                String s = read.get();
                return Objects.requireNonNullElse(s, NA);
            } catch (Exception e) {
                return NA;
            }
        }

        private static boolean eq(String a, String b) {
            if (a == null || b == null || List.of(a, b).contains(NA)) {
                return false;
            }
            return Objects.equals(a, b);
        }

        public static boolean sameSchema(Conn a, Conn b) {
            if (a == null || b == null) {
                return false;
            }
            return sameDatasource(a, b) && Objects.equals(a.catalogAndSchema, b.catalogAndSchema);
        }

        public static boolean sameDatasource(Conn a, Conn b) {
            if (a == null || b == null) {
                return false;
            }
            return eq(a.id, b.id);
        }

        public static Conn from(DBPDataSourceContainer dataSource, IEditorPart editor) {
            try {
                DBPConnectionConfiguration conf = dataSource.getConnectionConfiguration();
                CatalogAndSchema cs = CatalogAndSchema.from(editor);
                return new Conn(
                        read(dataSource::getId),
                        read(conf::getUserName),
                        read(conf::getHostName),
                        read(conf::getHostPort),
                        cs);
            } catch (Exception ex) {
                return new Conn(NA, NA, NA, NA, new CatalogAndSchema(null, null));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Conn conn = (Conn) o;
            return eq(id, conn.id)
                    && eq(ip, conn.ip)
                    && eq(user, conn.user)
                    && eq(port, conn.port)
                    && Objects.equals(catalogAndSchema, conn.catalogAndSchema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, user, ip, port, catalogAndSchema);
        }

        @Override
        public String text() {
            return "%s@%s:%s".formatted(user, ip, port);
        }

        record CatalogAndSchema(String catalog, String schema) implements ButtonObj.TextObj {
            static CatalogAndSchema from(IEditorPart activeEditor) {
                IEditorInput editorInput = activeEditor.getEditorInput();
                if (editorInput instanceof IDatabaseEditorInput) {
                    if (editorInput instanceof DatabaseLazyEditorInput) {
                        activeEditor.addPropertyListener(new IPropertyListener() {
                            @Override
                            public void propertyChanged(Object source, int propId) {
                                if (EntityEditor.PROP_TITLE == propId) {
                                    DataSourceToolbarUtils.updateCommandsUI();
                                    activeEditor.removePropertyListener(this);
                                }
                            }
                        });
                    }
                    DBCExecutionContext executionContext = ((IDatabaseEditorInput) editorInput).getExecutionContext();
                    if (executionContext != null) {
                        DBSObject schemaObject = DBUtils.getSelectedObject(executionContext);
                        if (schemaObject != null) {
                            DBSObject schemaParent = schemaObject.getParentObject();
                            if (schemaParent instanceof DBSObjectContainer
                                    && !(schemaParent instanceof DBPDataSource)) {
                                return new CatalogAndSchema(schemaParent.getName(), schemaObject.getName());
                            } else {
                                return new CatalogAndSchema(null, schemaObject.getName());
                            }
                        }
                    }
                } else {
                    DBCExecutionContext executionContext = getExecutionContextFromPart(activeEditor);
                    DBCExecutionContextDefaults<?, ?> contextDefaults = null;
                    if (executionContext != null) {
                        contextDefaults = executionContext.getContextDefaults();
                    }
                    if (contextDefaults != null) {
                        DBSCatalog defaultCatalog = contextDefaults.getDefaultCatalog();
                        DBSSchema defaultSchema = contextDefaults.getDefaultSchema();
                        if (defaultCatalog != null
                                && (defaultSchema != null || contextDefaults.supportsSchemaChange())) {
                            if (defaultSchema == null) {
                                return new CatalogAndSchema(null, null);
                            } else {
                                return new CatalogAndSchema(defaultCatalog.getName(), defaultSchema.getName());
                            }
                        } else if (defaultCatalog != null) {
                            return new CatalogAndSchema(defaultCatalog.getName(), null);
                        } else if (defaultSchema != null) {
                            return new CatalogAndSchema(null, defaultSchema.getName());
                        }
                    }
                }
                return new CatalogAndSchema(null, null);
            }

            @Override
            public String toString() {
                return text();
            }

            @Override
            public String text() {
                return catalog != null ? (schema != null ? "%s@%s".formatted(schema, catalog) : catalog) : "?";
            }
        }
    }
}