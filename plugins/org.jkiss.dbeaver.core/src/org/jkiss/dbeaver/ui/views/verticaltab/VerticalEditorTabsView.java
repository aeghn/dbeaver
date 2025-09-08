
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
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.navigator.*;
import org.jkiss.dbeaver.ui.actions.datasource.DataSourceToolbarUtils;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class VerticalEditorTabsView extends ViewPart implements IPartListener {
    private static final Log log = Log.getLog(VerticalEditorTabsView.class);
    private Button curDatasource;
    private Button curCatalog;
    private Button curSchema;
    private ContentViewer tabsViewer;
    Map<TabInfo, Composite> tabComposites;
    Composite tabsContainer;
    private IWorkbenchPage workbenchPage;

    @Override
    public void createPartControl(Composite parent) {
        workbenchPage = getSite().getPage();
        workbenchPage.addPartListener(this);
        parent.setLayout(new GridLayout(1, false));
        createControlArea(parent);
        createTabsArea(parent);
        refreshTabs();
        parent.getDisplay().timerExec(300, new Runnable() {
            @Override
            public void run() {
                if (!parent.isDisposed()) {
                    refreshTabs();
                }
            }
        });
    }

    private void createControlArea(Composite parent) {
        Composite controlArea = new Composite(parent, SWT.NONE);
        controlArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout layout = new GridLayout(2, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        controlArea.setLayout(layout);
        curDatasource = new Button(controlArea, SWT.CHECK);
        curDatasource.setText("<N/A>");
        curDatasource.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        curDatasource.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTabs();
            }
        });
        curCatalog = new Button(controlArea, SWT.CHECK);
        curCatalog.setText("<N/A>");
        curCatalog.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        curCatalog.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTabs();
            }
        });
        curSchema = new Button(controlArea, SWT.CHECK);
        curSchema.setText("<N/A>");
        curSchema.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        curSchema.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTabs();
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
        tabsViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

            @Override
            public void dispose() {}

            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });
        createContextMenu();
    }
    /**      * Clears all existing tabs from the container      */

    private void clearAllTabs() {
        for (Control control : tabsContainer.getChildren()) {
            if (control instanceof Composite) {
                control.dispose();
            }
        }
        tabComposites.clear();
    }
    /**      * Creates a custom tab item with icon, title, and close button      * Enhanced to show database and schema information in tooltip on hover      */

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
    /**      * Handles tab selection and activation      */

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

    private void refreshTabs() {
        List<TabInfo> tabs = new ArrayList<>();
        IEditorReference[] editorRefs = workbenchPage.getEditorReferences();
        IEditorPart activeEditor = workbenchPage.getActiveEditor();
        boolean filterCurrentDataSource = curDatasource.getSelection();
        boolean filterCurCatalog = curCatalog.getSelection();
        boolean filterCurSchema = curSchema.getSelection();
        DBPDataSourceContainer dataSource =
                DataSourceToolbarUtils.getCurrentDataSource(workbenchPage.getWorkbenchWindow());
        Conn conn = Conn.from(dataSource, activeEditor);
        if (dataSource != null) {
            curDatasource.setText("%s@%s:%s".formatted(conn.user, conn.ip, conn.port));
            curCatalog.setText(conn.catalog);
            curSchema.setText(conn.schema);
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
                } else if (filterCurCatalog) {
                    if (!Conn.sameCatalog(conn, connB)) {
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
                tabInfo.isActive = editorRef.equals(workbenchPage.getReference(activeEditor));
                tabInfo.isPinned = editorRef.isPinned();
                tabInfo.tooltip = editorRef.getTitleToolTip();
                tabs.add(tabInfo);
            }
        }
        tabs.sort((e1, e2) -> e1.isPinned ? 1 : -(e2.isPinned ? 1 : 0));
        tabsViewer.setInput(tabs);
        tabsViewer.refresh();
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
        }
    }

    private void updateTabAppearance(Composite tabComposite, TabInfo tabInfo) {
        if (tabInfo.isActive) {
            Color bgColor = tabComposite.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
            Color fgColor = tabComposite.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
            tabComposite.setBackground(bgColor);
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
            Color defaultBg = tabComposite.getParent().getBackground();
            Color defaultFg = tabComposite.getParent().getForeground();
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
        super.dispose();
    }

    @Override
    public void partOpened(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            refreshTabs();
        }
    }

    @Override
    public void partClosed(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            refreshTabs();
        }
    }

    @Override
    public void partActivated(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            refreshTabs();
        }
    }

    @Override
    public void partDeactivated(IWorkbenchPart part) {}

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {}

    private static class TabInfo {
        IEditorReference editorReference;
        String title;
        Image image;
        String tooltip;
        boolean isActive;
        boolean isPinned;
        Conn conn;
    }

    private record Conn(String id, String user, String ip, String port, String catalog, String schema) {
        private static String NA = "<N/A>";

        private static String read(Supplier<String> read) {
            try {
                String s = read.get();
                if (s == null) {
                    return NA;
                }
                return s;
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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Conn conn = (Conn) o;
            return eq(id, conn.id)
                    && eq(ip, conn.ip)
                    && eq(user, conn.user)
                    && eq(port, conn.port)
                    && eq(schema, conn.schema)
                    && eq(catalog, conn.catalog);
        }

        public static boolean sameDatasource(Conn a, Conn b) {
            if (a == null || b == null) {
                return false;
            }
            return eq(a.id, b.id);
        }

        public static boolean sameCatalog(Conn a, Conn b) {
            if (a == null || b == null) {
                return false;
            }
            return sameDatasource(a, b) && eq(a.catalog, b.catalog);
        }

        public static boolean sameSchema(Conn a, Conn b) {
            if (a == null || b == null) {
                return false;
            }
            return sameCatalog(a, b) && eq(a.schema, b.schema);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, user, ip, port, catalog, schema);
        }

        public static Conn from(DBPDataSourceContainer dataSource, IEditorPart editorInput) {
            DBPConnectionConfiguration conf = null;
            try {
                DBCExecutionContext executionContext;
                if (editorInput.getEditorInput() instanceof IDatabaseEditorInput edi) {
                    executionContext = edi.getExecutionContext();
                } else {
                    executionContext = null;
                }
                conf = dataSource.getConnectionConfiguration();
                return new Conn(
                        read(dataSource::getId),
                        read(conf::getUserName),
                        read(conf::getHostName),
                        read(conf::getHostPort),
                        read(() -> executionContext
                                .getContextDefaults()
                                .getDefaultCatalog()
                                .getName()),
                        read(() -> executionContext
                                .getContextDefaults()
                                .getDefaultSchema()
                                .getName()));
            } catch (Exception ex) {
                return new Conn(NA, NA, NA, NA, NA, NA);
            }
        }
    }
}
