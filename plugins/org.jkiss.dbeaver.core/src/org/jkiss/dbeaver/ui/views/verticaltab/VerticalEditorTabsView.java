package org.jkiss.dbeaver.ui.views.verticaltab;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.*;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceContainerProvider;
import org.jkiss.dbeaver.ui.actions.datasource.DataSourceToolbarUtils;

import java.util.*;

public class VerticalEditorTabsView extends ViewPart implements IPartListener, IElementUpdater {
    private static final int REFRESH_DELAY_MS = 300;
    private static final String NA = "?";

    private Composite tabsContainer;
    private TextButton<Database.Datasource> curDatasource;
    private TextButton<Database.CatalogAndSchema> curSchema;
    private ContentViewer tabsViewer;
    private ScrolledComposite scrolledComposite;

    private Map<TabItem, Composite> tabComposites = new HashMap<>();
    private TabItem lastActiveTab;
    private IWorkbenchPage workbenchPage;

    @Override
    public void createPartControl(Composite parent) {
        workbenchPage = getSite().getPage();
        workbenchPage.addPartListener(this);

        parent.setLayout(new GridLayout(1, false));
        createControlArea(parent);
        createTabsArea(parent);

        refreshAll();

        parent.getDisplay().timerExec(REFRESH_DELAY_MS, () -> {
            if (!parent.isDisposed()) {
                refreshAll();
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
        curDatasource = new TextButton<>("", dsBut);
        dsBut.setText(NA);
        dsBut.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        dsBut.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> refreshAll()));

        Button schemaBut = new Button(controlArea, SWT.CHECK);
        curSchema = new TextButton<>("", schemaBut);
        schemaBut.setText(NA);
        schemaBut.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        schemaBut.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> refreshAll()));
    }


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

        initTabsViewer();
    }

    private void initTabsViewer() {
        tabsViewer = new ContentViewer() {
            private List<TabItem> currentInput;

            @Override
            protected void inputChanged(Object input, Object oldInput) {
                // 检查控件是否已销毁
                if (tabsContainer.isDisposed() || currentInput == null) {
                    return;
                }

                clearAllTabs();
                Set<TabItem> processedTabs = new HashSet<>();

                for (TabItem tabInfo : currentInput) {
                    if (!processedTabs.contains(tabInfo)) {
                        Composite tabComposite = tabInfo.getTabComposite();

                        // 检查复合控件是否有效
                        if (tabComposite != null && !tabComposite.isDisposed()) {
                            // 确保复合控件被添加到正确的父容器
                            if (tabComposite.getParent() != tabsContainer) {
                                tabComposite.setParent(tabsContainer);
                            }
                            tabComposites.put(tabInfo, tabComposite);
                            processedTabs.add(tabInfo);
                        }
                    }
                }

                updateContainerLayout();
            }

            @Override
            public void refresh() {
                if (currentInput != null) {
                    clearAllTabs();
                    Set<TabItem> processedTabs = new HashSet<>();

                    for (TabItem tabItem : currentInput) {
                        if (!processedTabs.contains(tabItem)) {
                            tabItem.update();
                            processedTabs.add(tabItem);
                        }
                    }

                    updateContainerLayout();
                    tabsContainer.redraw(); // 添加重绘
                    tabsContainer.update(); // 强制更新
                }
            }

            @Override
            public Object getInput() {
                return currentInput;
            }

            @Override
            public ISelection getSelection() {
                for (Map.Entry<TabItem, Composite> entry : tabComposites.entrySet()) {
                    if (entry.getKey().isActive()) {
                        return new StructuredSelection(entry.getKey());
                    }
                }
                return StructuredSelection.EMPTY;
            }

            @Override
            public void setSelection(ISelection selection, boolean reveal) {
            }

            @Override
            public Control getControl() {
                return scrolledComposite;
            }
        };

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
            }

            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            }
        });
    }

    private void updateContainerLayout() {
        if (tabsContainer.isDisposed() || scrolledComposite.isDisposed()) {
            return;
        }

        tabsContainer.layout(true, true);
        scrolledComposite.setMinSize(tabsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    private void clearAllTabs() {
        for (Control control : tabsContainer.getChildren()) {
            if (!control.isDisposed()) {
                control.dispose();
            }
        }
        tabComposites.clear();

        // 强制布局更新
        if (!tabsContainer.isDisposed()) {
            tabsContainer.layout(true, true);
        }
    }


    private void refreshAll() {
        try {
            refreshAll(true, true);
        } catch (Exception ex) {
            Log.getLog(VerticalEditorTabsView.class).error("refreshAll", ex);
        }
    }

    private void refreshAll(boolean controlFlag, boolean tabsFlag) {
        List<TabItem> tabs = new ArrayList<>();
        IEditorReference[] editorRefs = workbenchPage.getEditorReferences();
        IEditorPart activeEditor = workbenchPage.getActiveEditor();

        // 添加 null 检查
        if (activeEditor == null) {
            // 如果没有活动编辑器，清空控件并返回
            if (controlFlag) {
                updateControlArea(null);
            }
            tabsViewer.setInput(Collections.emptyList());
            return;
        }

        boolean filterCurrentDataSource = curDatasource.isSelected();
        boolean filterCurSchema = curSchema.isSelected();

        DBPDataSourceContainer dataSource =
                DataSourceToolbarUtils.getCurrentDataSource(workbenchPage.getWorkbenchWindow());
        Database conn = Database.from(Arrays.stream(editorRefs)
                .filter(e -> e.getEditor(false) == activeEditor)
                .findAny()
                .orElse(null), dataSource);

        validateLastActiveTab(editorRefs);

        if (controlFlag) {
            updateControlArea(conn);
        }

        if (!tabsFlag) {
            return;
        }

        collectTabInfo(
                tabs,
                editorRefs,
                activeEditor,
                conn,
                filterCurrentDataSource,
                filterCurSchema);

        tabs.sort((e1, e2) -> Boolean.compare(e2.isPinned(), e1.isPinned()));
        tabsViewer.setInput(tabs);
        tabsViewer.refresh();
    }



    private void validateLastActiveTab(IEditorReference[] editorRefs) {
        if (lastActiveTab != null) {
            boolean editorStillOpen = false;
            for (IEditorReference ref : editorRefs) {
                if (ref.equals(lastActiveTab.getEditorReference())) {
                    editorStillOpen = true;
                    break;
                }
            }
            if (!editorStillOpen) {
                lastActiveTab = null;
            }
        }
    }


    private void updateControlArea(Database conn) {
        if (conn != null) {
            curDatasource.update(conn.datasource());
            curSchema.update(conn.catalogAndSchema());
        } else {
            curDatasource.update(null);
            curSchema.update(null);
        }
    }


    private void collectTabInfo(
            List<TabItem> tabs,
            IEditorReference[] editorRefs,
            IEditorPart activeEditor,
            Database conn,
            boolean filterCurrentDataSource,
            boolean filterCurSchema) {
        for (IEditorReference editorRef : editorRefs) {
            if (editorRef != null) {
                Database connB = null;
                if (editorRef instanceof DBPDataSourceContainerProvider) {
                    connB = Database.from(editorRef, null);
                }

                // 应用过滤器
                if (filterCurSchema && !Database.sameSchema(conn, connB)) {
                    continue;
                } else if (filterCurrentDataSource && !Database.sameDatasource(conn, connB)) {
                    continue;
                }

                var tabItem = TabItem.create(editorRef, this.tabsContainer);

                tabs.add(tabItem);
            }
        }
    }



//    private void updateActiveTabAppearance(IEditorPart activeEditor) {
//        if (activeEditor != null) {
//            var activeRef = workbenchPage.getReference(activeEditor);
//            for (TabItem tab : tabComposites.keySet()) {
//                if (tab.getEditorReference().equals(activeRef)) {
//                    Composite tabComposite = tabComposites.get(tab);
//                    if (tabComposite != null && !tabComposite.isDisposed()) {
//                        updateTabAppearance(tabComposite, tab);
//                    }
//                    break;
//                }
//            }
//        } else if (lastActiveTab != null) {
//            Composite tabComposite = tabComposites.get(lastActiveTab);
//            if (tabComposite != null && !tabComposite.isDisposed()) {
//                updateTabAppearance(tabComposite, lastActiveTab);
//            }
//        }
//    }


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
}