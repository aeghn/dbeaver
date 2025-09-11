package org.jkiss.dbeaver.ui.views.verticaltab;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.*;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.internal.WorkbenchWindow;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.ui.UITextUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.actions.datasource.DataSourceToolbarUtils;
import org.jkiss.dbeaver.ui.editors.DatabaseLazyEditorInput;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;

import java.util.*;
import java.util.function.Supplier;

import static org.jkiss.dbeaver.ui.actions.AbstractDataSourceHandler.getExecutionContextFromPart;

/**
 * VerticalEditorTabsView - 垂直编辑器标签视图
 * 优化版本：改进了性能、资源管理和代码结构
 */
public class VerticalEditorTabsView extends ViewPart implements IPartListener, IElementUpdater {
    private static final Log log = Log.getLog(VerticalEditorTabsView.class);

    // 常量定义
    private static final int REFRESH_DELAY_MS = 300;
    private static final String NA = "?";
    private static final String TAB_INFO_KEY = "tabInfo";

    // UI 组件
    private Composite tabsContainer;
    private ButtonObj<Conn> curDatasource;
    private ButtonObj<Conn.CatalogAndSchema> curSchema;
    private ContentViewer tabsViewer;
    private ScrolledComposite scrolledComposite;

    // 数据状态
    private Map<TabInfo, Composite> tabComposites = new HashMap<>();
    private TabInfo lastActiveTab;
    private IWorkbenchPage workbenchPage;

    @Override
    public void createPartControl(Composite parent) {
        workbenchPage = getSite().getPage();
        workbenchPage.addPartListener(this);

        parent.setLayout(new GridLayout(1, false));
        createControlArea(parent);
        createTabsArea(parent);

        // 初始刷新
        refreshAll();

        // 延迟刷新以确保UI完全加载
        parent.getDisplay().timerExec(REFRESH_DELAY_MS, () -> {
            if (!parent.isDisposed()) {
                refreshAll();
            }
        });
    }

    /**
     * 创建控制区域（数据源和模式选择）
     */
    private void createControlArea(Composite parent) {
        Composite controlArea = new Composite(parent, SWT.NONE);
        controlArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        controlArea.setLayout(layout);

        // 数据源选择按钮
        Button dsBut = new Button(controlArea, SWT.CHECK);
        curDatasource = new ButtonObj<>("", dsBut);
        dsBut.setText(NA);
        dsBut.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        dsBut.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> refreshAll()));

        // 模式选择按钮
        Button schemaBut = new Button(controlArea, SWT.CHECK);
        curSchema = new ButtonObj<>("", schemaBut);
        schemaBut.setText(NA);
        schemaBut.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        schemaBut.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> refreshAll()));
    }

    /**
     * 创建标签区域
     */
    private void createTabsArea(Composite parent) {
        scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
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

        // 初始化标签查看器
        initTabsViewer();
        createContextMenu();
    }

    /**
     * 初始化标签查看器
     */
    private void initTabsViewer() {
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

                    updateContainerLayout();
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

        // 设置内容提供器
        tabsViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }

            @Override
            public void dispose() {
                // 无资源需要释放
            }

            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
                // 处理在inputChanged中
            }
        });
    }

    /**
     * 更新容器布局和滚动大小
     */
    private void updateContainerLayout() {
        tabsContainer.layout();
        scrolledComposite.setMinSize(tabsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    /**
     * 清除所有标签
     */
    private void clearAllTabs() {
        for (Control control : tabsContainer.getChildren()) {
            if (!control.isDisposed()) {
                control.dispose();
            }
        }
        tabComposites.clear();
    }

    /**
     * 创建单个标签项
     */
    private void createTabItem(Composite parent, TabInfo tabInfo) {
        if (tabComposites.containsKey(tabInfo)) {
            return;
        }

        Composite tabComposite = new Composite(parent, SWT.NONE);
        tabComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        tabComposite.setData(TAB_INFO_KEY, tabInfo);
        tabComposites.put(tabInfo, tabComposite);

        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 3;
        layout.marginHeight = 1;
        layout.horizontalSpacing = 3;
        tabComposite.setLayout(layout);

        // 添加标签点击监听
        addTabSelectionListener(tabComposite, tabInfo);

        // 创建图标标签
        Label iconLabel = createIconLabel(tabComposite, tabInfo);

        // 创建标题标签
        Label titleLabel = createTitleLabel(tabComposite, tabInfo);

        // 创建关闭按钮
        Button closeButton = createCloseButton(tabComposite, tabInfo);

        // 初始更新外观
        updateTabAppearance(tabComposite, tabInfo);
    }

    /**
     * 添加标签选择监听器
     */
    private void addTabSelectionListener(Composite tabComposite, TabInfo tabInfo) {
        MouseAdapter selectionAdapter = new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) { // 左键点击
                    selectTab(tabComposite, tabInfo);
                }
            }
        };

        tabComposite.addMouseListener(selectionAdapter);
    }

    /**
     * 创建图标标签
     */
    private Label createIconLabel(Composite parent, TabInfo tabInfo) {
        Label iconLabel = new Label(parent, SWT.NONE);
        if (tabInfo.image != null) {
            iconLabel.setImage(tabInfo.image);
        }
        iconLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // 图标也可点击选择标签
        iconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    selectTab(parent, tabInfo);
                }
            }
        });

        return iconLabel;
    }

    /**
     * 创建标题标签
     */
    private Label createTitleLabel(Composite parent, TabInfo tabInfo) {
        Label titleLabel = new Label(parent, SWT.NONE);
        titleLabel.setText(tabInfo.title);
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 标题也可点击选择标签
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    Composite tabComposite = (Composite) parent.getParent();
                    selectTab(tabComposite, tabInfo);
                }
            }
        });

        return titleLabel;
    }

    /**
     * 创建关闭按钮
     */
    private Button createCloseButton(Composite parent, TabInfo tabInfo) {
        Button closeButton = new Button(parent, SWT.PUSH | SWT.FLAT);
        closeButton.setText("×");
        closeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        closeButton.setToolTipText("Close tab");
        closeButton.setBackground(parent.getBackground());

        // 鼠标悬停效果
        closeButton.addMouseTrackListener(new MouseTrackAdapter() {
            @Override
            public void mouseEnter(MouseEvent e) {
                closeButton.setBackground(closeButton.getDisplay().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
            }

            @Override
            public void mouseExit(MouseEvent e) {
                closeButton.setBackground(parent.getBackground());
            }
        });

        // 关闭按钮点击事件
        closeButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            if (tabInfo.editorReference != null) {
                workbenchPage.closeEditor(tabInfo.editorReference, true);
                tabComposites.remove(tabInfo);
            }
        }));

        return closeButton;
    }

    /**
     * 选择标签
     */
    private void selectTab(Composite tabComposite, TabInfo tabInfo) {
        // 取消所有标签的高亮
        for (Composite comp : tabComposites.values()) {
            if (!comp.isDisposed()) {
                TabInfo info = (TabInfo) comp.getData(TAB_INFO_KEY);
                if (info != null) {
                    info.isActive = false;
                    updateTabAppearance(comp, info);
                }
            }
        }

        // 高亮当前选中标签
        tabInfo.isActive = true;
        updateTabAppearance(tabComposite, tabInfo);
        lastActiveTab = tabInfo;

        // 激活对应编辑器
        if (tabInfo.editorReference != null) {
            try {
                workbenchPage.activate(tabInfo.editorReference);
            } catch (Exception e) {
                log.error("Error activating editor: " + tabInfo.title, e);
            }
        }

        // 更新查看器选择
        if (tabsViewer != null) {
            tabsViewer.setSelection(new StructuredSelection(tabInfo));
        }
    }

    /**
     * 创建上下文菜单
     */
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
                            workbenchPage.closeEditor(tabInfo.editorReference, true);
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

    /**
     * 关闭其他标签
     */
    private void closeOtherTabs(TabInfo keepTab) {

    }

    /**
     * 关闭所有标签
     */
    private void closeAllTabs() {
        workbenchPage.closeAllEditors(false);
    }

    /**
     * 刷新所有内容
     */
    private void refreshAll() {
        refreshAll(true, true);
    }

    /**
     * 刷新所有内容（带控制标志）
     */
    private void refreshAll(boolean controlFlag, boolean tabsFlag) {
        List<TabInfo> tabs = new ArrayList<>();
        IEditorReference[] editorRefs = workbenchPage.getEditorReferences();
        IEditorPart activeEditor = workbenchPage.getActiveEditor();

        boolean filterCurrentDataSource = curDatasource.isSelected();
        boolean filterCurSchema = curSchema.isSelected();

        DBPDataSourceContainer dataSource =
                DataSourceToolbarUtils.getCurrentDataSource(workbenchPage.getWorkbenchWindow());
        Conn conn = Conn.from(dataSource, activeEditor, workbenchPage.getWorkbenchWindow());

        // 检查 lastActiveTab 是否仍然有效
        validateLastActiv
    eTab(editorRefs);

        if (controlFlag) {
            updateControlArea(dataSource, conn);
        }

        if (!tabsFlag) {
            return;
        }

        // 收集标签信息
        collectTabInfo(
                tabs,
                editorRefs,
                activeEditor,
                conn,
                filterCurrentDataSource,
                filterCurSchema,
                workbenchPage.getWorkbenchWindow());

        // 排序并更新查看器
        tabs.sort((e1, e2) -> Boolean.compare(e2.isPinned, e1.isPinned)); // 固定标签优先
        tabsViewer.setInput(tabs);
        tabsViewer.refresh();

        // 更新活动标签外观
        updateActiveTabAppearance(activeEditor);
    }

    /**
     * 验证最后活动标签是否有效
     */
    private void validateLastActiveTab(IEditorPart[] editorRefs) {
        if (lastActiveTab != null) {
            boolean editorStillOpen = false;
            for (IEditorPart ref : editorRefs) {
                if (ref.equals(lastActiveTab.editorReference)) {
                    editorStillOpen = true;
                    break;
                }
            }
            if (!editorStillOpen) {
                lastActiveTab = null;
            }
        }
    }

    /**
     * 更新控制区域
     */
    private void updateControlArea(DBPDataSourceContainer dataSource, Conn conn) {
        if (dataSource != null) {
            curDatasource.updateT(conn);
            curSchema.updateT(conn.catalogAndSchema);
        } else {
            curDatasource.updateT(null);
            curSchema.updateT(null);
        }
    }

    /**
     * 收集标签信息
     */
    private void collectTabInfo(
            List<TabInfo> tabs,
            IEditorPart[] editorRefs,
            IEditorPart activeEditor,
            Conn conn,
            boolean filterCurrentDataSource,
            boolean filterCurSchema,
            IWorkbenchWindow workbenchWindow) {
        for (IEditorPart editorRef : editorRefs) {
            IEditorPart editor = editorRef;
            if (editor != null) {
                Conn connB = null;
                if (editor instanceof DBPDataSourceContainerProvider) {
                    connB = Conn.from(((DBPDataSourceContainerProvider) editor).getDataSourceContainer(), editor, workbenchWindow);
                }

                // 应用过滤器
                if (filterCurSchema && !Conn.sameSchema(conn, connB)) {
                    continue;
                } else if (filterCurrentDataSource && !Conn.sameDatasource(conn, connB)) {
                    continue;
                }

                // 创建标签信息
                TabInfo tabInfo = createTabInfo(editorRef, editor, connB, activeEditor);
                tabs.add(tabInfo);
            }
        }
    }

    /**
     * 创建标签信息对象
     */
    private TabInfo createTabInfo(
            IEditorPart editorRef, IEditorPart editor, Conn connB, IEditorPart activeEditor) {
        TabInfo tabInfo = new TabInfo();
        tabInfo.editorReference = editorRef;
        tabInfo.title = editorRef.getTitle();
        tabInfo.image = editorRef.getTitleImage();
        tabInfo.conn = connB;

        // 设置活动状态
        if (activeEditor != null) {
            tabInfo.isActive = editorRef.equals(workbenchPage.getReference(activeEditor));
            if (tabInfo.isActive) {
                lastActiveTab = tabInfo;
            }
        } else {
            tabInfo.isActive = (lastActiveTab != null && lastActiveTab.editorReference.equals(editorRef));
        }

        tabInfo.tooltip = editorRef.getTitleToolTip();

        return tabInfo;
    }

    /**
     * 更新活动标签外观
     */
    private void updateActiveTabAppearance(IEditorPart activeEditor) {
        if (activeEditor != null) {
            var activeRef = workbenchPage.getReference(activeEditor);
            for (TabInfo tab : tabComposites.keySet()) {
                if (tab.editorReference.equals(activeRef)) {
                    Composite tabComposite = tabComposites.get(tab);
                    if (tabComposite != null && !tabComposite.isDisposed()) {
                        updateTabAppearance(tabComposite, tab);
                    }
                    break;
                }
            }
        } else if (lastActiveTab != null) {
            Composite tabComposite = tabComposites.get(lastActiveTab);
            if (tabComposite != null && !tabComposite.isDisposed()) {
                updateTabAppearance(tabComposite, lastActiveTab);
            }
        }
    }

    /**
     * 更新标签外观
     */
    private void updateTabAppearance(Composite tabComposite, TabInfo tabInfo) {
        if (tabComposite.isDisposed()) {
            return;
        }

        if (tabInfo.isActive) {
            tabComposite.setBackgroundMode(SWT.INHERIT_NONE);

            for (Control child : tabComposite.getChildren()) {
                if (child.isDisposed()) continue;

                child.setFont(UIUtils.makeBoldFont(child.getFont()));
            }
        } else {

            for (Control child : tabComposite.getChildren()) {
                if (child.isDisposed()) continue;

                child.setToolTipText(tabInfo.tooltip);
            }
        }

        //        tabComposite.redraw();
        //        tabComposite.update();
    }

    @Override
    public void setFocus() {
        if (tabsViewer != null && !tabsViewer.getControl().isDisposed()) {
            tabsViewer.getControl().setFocus();
        }
    }

    @Override
    public void dispose() {
        if (workbenchPage != null) {
            workbenchPage.removePartListener(this);
        }

        lastActiveTab = null;
        tabComposites.clear();

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
    public void partDeactivated(IWorkbenchPart part) {
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
    }

    @Override
    public void updateElement(UIElement element, Map parameters) {
        if ("true".equals(parameters.get("noCustomLabel"))) {
            return;
        }
        refreshAll(true, false);
    }


    private static class TabInfo {
        IEditorPart editorReference;
        String title;
        Image image;
        String tooltip;
        boolean isActive;
        boolean isPinned;
        Conn conn;
    }

    private record Conn(String id, String name, String user, String ip, String port, CatalogAndSchema catalogAndSchema)
            implements ButtonObj.TextObj {

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

        private static boolean eq(String a, String b) {
            if (a == null || b == null || List.of(a, b).contains(NA)) {
                return false;
            }
            return Objects.equals(a, b);
        }

        private static String read(Supplier<String> read) {
            try {
                String s = read.get();
                return Objects.requireNonNullElse(s, NA);
            } catch (Exception e) {
                return NA;
            }
        }

        public static Conn from(DBPDataSourceContainer dataSource, IEditorPart editor, IWorkbenchWindow workbenchWindow) {
            try {
                DBPConnectionConfiguration conf = dataSource.getConnectionConfiguration();
                CatalogAndSchema cs = CatalogAndSchema.from(editor);
                String connectionName = dataSource.getName();
                if (workbenchWindow != null) {
                    GC gc = new GC(workbenchWindow.getShell());
                    try {
                        connectionName = UITextUtils.getShortText(gc, connectionName, 200);
                    } finally {
                        gc.dispose();
                    }
                }

                return new Conn(
                        read(dataSource::getId),
                        connectionName,
                        read(conf::getUserName),
                        read(conf::getHostName),
                        read(conf::getHostPort),
                        cs);
            } catch (Exception ex) {
                return new Conn(null, null, null, null, null, new CatalogAndSchema(null, null));
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
            return ip != null ? "%s@%s:%s".formatted(user, ip, port) : this.name;
        }

        /**
         * 目录和模式信息
         */
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