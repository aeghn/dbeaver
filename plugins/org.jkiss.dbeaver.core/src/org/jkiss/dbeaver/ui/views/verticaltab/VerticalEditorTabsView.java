package org.jkiss.dbeaver.ui.views.verticaltab;/* DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
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
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.struct.DBSInstance;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * VerticalEditorTabsView
 */
public class VerticalEditorTabsView extends ViewPart implements IPartListener {
    private static final Log log = Log.getLog(VerticalEditorTabsView.class);

    private Composite controlArea;
    private Button currentDatabaseCheck;
    private Button currentSchemaCheck;
    private ContentViewer tabsViewer;
    // Store references to created tabs for management
    Map<TabInfo, Composite> tabComposites;
    // Create a container for all tab items
    Composite tabsContainer;
    private IWorkbenchPage workbenchPage;


    @Override
    public void createPartControl(Composite parent) {
        workbenchPage = getSite().getPage();
        workbenchPage.addPartListener(this);

        // 创建上下两部分布局
        parent.setLayout(new GridLayout(1, false));

        // 创建控制区域
        createControlArea(parent);

        // 创建标签页展示区域
        createTabsArea(parent);

        // 初始化数据
        refreshTabs();
    }

    private void createControlArea(Composite parent) {
        controlArea = new Composite(parent, SWT.NONE);
        controlArea.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout layout = new GridLayout(2, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        controlArea.setLayout(layout);

        currentDatabaseCheck = new Button(controlArea, SWT.CHECK);
        currentDatabaseCheck.setText("当前数据库");
        currentDatabaseCheck.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        currentDatabaseCheck.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTabs();
            }
        });

        currentSchemaCheck = new Button(controlArea, SWT.CHECK);
        currentSchemaCheck.setText("当前Schema");
        currentSchemaCheck.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        currentSchemaCheck.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTabs();
            }
        });
    }


    private void createTabsArea(Composite parent) {
        // Create a ScrolledComposite to handle overflow when many tabs are present
        ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.BORDER);
        scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolledComposite.setExpandHorizontal(true);
        scrolledComposite.setExpandVertical(true);

        // Create a container for all tab items
        tabsContainer = new Composite(scrolledComposite, SWT.NONE);
        scrolledComposite.setContent(tabsContainer);

        // Use a GridLayout with 1 column for vertical stacking of tabs
        GridLayout containerLayout = new GridLayout(1, false);
        containerLayout.marginWidth = 0;
        containerLayout.marginHeight = 0;
        containerLayout.verticalSpacing = 1; // Small gap between tabs
        tabsContainer.setLayout(containerLayout);

        // Store references to created tabs for management
        tabComposites = new HashMap<>();

        // Set up the content provider for the tabs
        tabsViewer = new ContentViewer() {
            private List<TabInfo> currentInput;

            @Override
            protected void inputChanged(Object input, Object oldInput) {
                // Clear all existing tabs before creating new ones
                clearAllTabs();

                if (input instanceof List) {
                    currentInput = (List<TabInfo>) input;
                    refresh();
                }
            }

            @Override
            public void refresh() {
                if (currentInput != null) {
                    // Clear existing tabs to prevent duplicates
                    clearAllTabs();

                    // Create a tab for each TabInfo, checking for duplicates
                    Set<TabInfo> processedTabs = new HashSet<>();
                    for (TabInfo tabInfo : currentInput) {
                        // Prevent duplicate tab creation
                        if (!processedTabs.contains(tabInfo)) {
                            createTabItem(tabsContainer, tabInfo);
                            processedTabs.add(tabInfo);
                        }
                    }

                    // Layout the container and set min size for scrolling
                    tabsContainer.layout();
                    scrolledComposite.setMinSize(tabsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));

                    // Update scrollbar if needed
                    scrolledComposite.setMinSize(tabsContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT));
                }
            }

            @Override
            public Object getInput() {
                return currentInput;
            }

            @Override
            public ISelection getSelection() {
                // Find the selected tab
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
                    if (firstElement instanceof TabInfo) {
                        TabInfo tabInfo = (TabInfo) firstElement;
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

        // Set the content provider (simplified version)
        tabsViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
                // Let the ContentViewer handle input changes
            }

            @Override
            public void dispose() {
                // Clean up resources if needed
            }

            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });

        // Create context menu
        createContextMenu();
    }

    /**
     * Clears all existing tabs from the container
     */
    private void clearAllTabs() {
        // Dispose of all tab composites
        for (Control control : tabsContainer.getChildren()) {
            if (control instanceof Composite) {
                control.dispose();
            }
        }

        // Clear the tracking map
        tabComposites.clear();
    }

    /**
     * Creates a custom tab item with icon, title, and close button
     */
    private void createTabItem(Composite parent, TabInfo tabInfo) {
        // Check if this tab already exists to prevent duplicates
        if (tabComposites.containsKey(tabInfo)) {
            return;
        }

        // Create a composite for the tab with a grid layout
        Composite tabComposite = new Composite(parent, SWT.NONE);
        tabComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        tabComposite.setData("tabInfo", tabInfo); // Store reference to tab data

        // Track this tab composite
        tabComposites.put(tabInfo, tabComposite);

        // Use a GridLayout with 3 columns: icon, title, close button
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 5;
        layout.marginHeight = 3;
        layout.horizontalSpacing = 5;
        tabComposite.setLayout(layout);

        // Add mouse listener for selection to the entire tab composite
        tabComposite.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                // Only activate on left click, not on close button clicks
                if (e.button == 1) {
                    selectTab(tabComposite, tabInfo);
                }
            }
        });

        // Icon label
        Label iconLabel = new Label(tabComposite, SWT.NONE);
        if (tabInfo.image != null) {
            iconLabel.setImage(tabInfo.image);
        }
        iconLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Add mouse listener to the icon for selection
        iconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    selectTab(tabComposite, tabInfo);
                }
            }
        });

        // Title label
        Label titleLabel = new Label(tabComposite, SWT.NONE);
        titleLabel.setText(tabInfo.title);
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Add mouse listener to the title for selection
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) {
                    selectTab(tabComposite, tabInfo);
                }
            }
        });

        // Close button with hover effects
        Button closeButton = new Button(tabComposite, SWT.PUSH | SWT.FLAT);
        closeButton.setText("×");
        closeButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        closeButton.setToolTipText("Close tab");

        // Set initial button appearance
        closeButton.setBackground(tabComposite.getBackground());

        // Add hover effects using MouseTrackListener
        closeButton.addMouseTrackListener(new MouseTrackAdapter() {
            @Override
            public void mouseEnter(MouseEvent e) {
                // Change background on hover
                closeButton.setBackground(closeButton.getDisplay().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW));
            }

            @Override
            public void mouseExit(MouseEvent e) {
                // Restore original background
                closeButton.setBackground(tabComposite.getBackground());
            }
        });

        // Add selection listener for the close button
        closeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // Close the tab when the close button is clicked
                if (tabInfo.editorReference != null) {
                    workbenchPage.closeEditor(tabInfo.editorReference.getEditor(false), true);

                    // Remove from our tracking map
                    tabComposites.remove(tabInfo);
                }
            }
        });

        // Set tooltip for the entire tab
        tabComposite.setToolTipText(tabInfo.title);

        // Style based on active state
        updateTabAppearance(tabComposite, tabInfo);
    }

    /**
     * Handles tab selection and activation
     */
    private void selectTab(Composite tabComposite, TabInfo tabInfo) {
        // Reset all tabs to inactive appearance
        for (Composite comp : tabComposites.values()) {
            TabInfo info = (TabInfo) comp.getData("tabInfo");
            if (info != null) {
                info.isActive = false;
                updateTabAppearance(comp, info);
            }
        }

        // Set this tab as active
        tabInfo.isActive = true;
        updateTabAppearance(tabComposite, tabInfo);

        // Activate the editor
        if (tabInfo.editorReference != null) {
            try {
                workbenchPage.activate(tabInfo.editorReference.getPart(true));
            } catch (Exception e) {
                // Log error but don't crash
                System.err.println("Error activating editor: " + e.getMessage());
            }
        }

        // Notify selection change if needed
        if (tabsViewer != null) {
            tabsViewer.setSelection(new StructuredSelection(tabInfo));
        }
    }

    private void createContextMenu() {
        MenuManager menuMgr = new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                IStructuredSelection selection = (IStructuredSelection) tabsViewer.getSelection();
                if (!selection.isEmpty()) {
                    TabInfo tabInfo = (TabInfo) selection.getFirstElement();

                    manager.add(new Action("关闭") {
                        @Override
                        public void run() {
                            if (tabInfo.editorReference != null) {
                                workbenchPage.closeEditor(tabInfo.editorReference.getEditor(false), true);
                            }
                        }
                    });

                    manager.add(new Action("关闭其他") {
                        @Override
                        public void run() {
                            closeOtherTabs(tabInfo);
                        }
                    });

                    manager.add(new Action("关闭所有") {
                        @Override
                        public void run() {
                            closeAllTabs();
                        }
                    });
                }
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

    /**
     * Refreshes the tabs list based on current filters and editor state
     * Enhanced to ensure proper highlighting of active tab
     */
    private void refreshTabs() {
        List<TabInfo> tabs = new ArrayList<>();
        IEditorReference[] editorRefs = workbenchPage.getEditorReferences();
        IEditorPart activeEditor = workbenchPage.getActiveEditor();

        boolean filterCurrentDatabase = currentDatabaseCheck.getSelection();
        boolean filterCurrentSchema = currentSchemaCheck.getSelection();

        DBCExecutionContext currentExecutionContext = null;
        if (filterCurrentDatabase || filterCurrentSchema) {
            IEditorPart editor = workbenchPage.getActiveEditor();
            if (editor != null) {
                IEditorInput editorInput = editor.getEditorInput();
                if (editorInput instanceof IDatabaseEditorInput edei) {
                    currentExecutionContext = edei.getExecutionContext();
                }
            }
        }

        for (IEditorReference editorRef : editorRefs) {
            IEditorPart editor = editorRef.getEditor(false);
            if (editor != null) {
                // Apply filters
                if ((filterCurrentDatabase || filterCurrentSchema) && editor instanceof IDatabaseEditorInput edei) {
                    DBCExecutionContext context = edei.getExecutionContext();
                    if (context != null && currentExecutionContext != null) {
                        // Check database filter
                        if (filterCurrentDatabase) {
                            DBSInstance currentInstance = currentExecutionContext.getDataSource().getDefaultInstance();
                            DBSInstance tabInstance = context.getDataSource().getDefaultInstance();
                            if (currentInstance != tabInstance) {
                                continue;
                            }
                        }

                        // Check schema filter (commented out as in original code)
                        // if (filterCurrentSchema) {
                        //     DBSObject currentDefaultObject = currentExecutionContext.getDefaultCatalog();
                        //     if (currentDefaultObject == null) {
                        //         currentDefaultObject = currentExecutionContext.getDefaultSchema();
                        //     }
                        //     DBSObject tabDefaultObject = context.getDefaultCatalog();
                        //     if (tabDefaultObject == null) {
                        //         tabDefaultObject = context.getDefaultSchema();
                        //     }
                        //     if (currentDefaultObject != tabDefaultObject) {
                        //         continue;
                        //     }
                        // }
                    }
                }

                TabInfo tabInfo = new TabInfo();
                tabInfo.editorReference = editorRef;
                tabInfo.title = editorRef.getTitle();

                // FIX: Properly determine if this is the active tab
                // Compare both the editor reference and the active editor
                tabInfo.isActive = (activeEditor != null &&
                        editorRef.equals(workbenchPage.getReference(activeEditor)));

                // Get editor icon
                ImageDescriptor imageDesc = null;
                try {
                    imageDesc = editorRef.getEditorInput().getImageDescriptor();
                    if (imageDesc != null) {
                        tabInfo.image = imageDesc.createImage();
                    }
                } catch (PartInitException ignored) {
                    // Ignore exception, tab will be created without icon
                }

                tabs.add(tabInfo);
            }
        }

        // Set input and refresh the viewer
        tabsViewer.setInput(tabs);
        tabsViewer.refresh();

        // FIX: Ensure the active tab is visually highlighted after refresh
        if (activeEditor != null) {
            var activeRef = workbenchPage.getReference(activeEditor);
            for (TabInfo tab : tabs) {
                if (tab.editorReference.equals(activeRef)) {
                    Composite tabComposite = tabComposites.get(tab);
                    if (tabComposite != null) {
                        // Update appearance to ensure highlighting
                        updateTabAppearance(tabComposite, tab);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Updates the visual appearance of a tab based on its active state
     * Enhanced to ensure consistent highlighting
     */
    private void updateTabAppearance(Composite tabComposite, TabInfo tabInfo) {
        // FIX: Use system colors that work across different platforms and themes
        if (tabInfo.isActive) {
            // Use system selection colors for active tab
            Color bgColor = tabComposite.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
            Color fgColor = tabComposite.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);

            tabComposite.setBackground(bgColor);

            for (Control child : tabComposite.getChildren()) {
                if (child instanceof Label && !(child instanceof Button)) {
                    child.setBackground(bgColor);
                    child.setForeground(fgColor);
                } else if (child instanceof Button) {
                    // Set close button background to match selected tab
                    child.setBackground(bgColor);
                    child.setForeground(fgColor);
                }
            }
        } else {
            // Use default colors for inactive tabs
            Color defaultBg = tabComposite.getParent().getBackground();
            Color defaultFg = tabComposite.getParent().getForeground();

            tabComposite.setBackground(defaultBg);

            for (Control child : tabComposite.getChildren()) {
                if (child instanceof Label && !(child instanceof Button)) {
                    child.setBackground(defaultBg);
                    child.setForeground(defaultFg);
                } else if (child instanceof Button) {
                    child.setBackground(defaultBg);
                    child.setForeground(defaultFg);
                }
            }
        }

        // FIX: Force redraw to ensure visual changes are applied immediately
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

    // IPartListener 实现
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
    public void partDeactivated(IWorkbenchPart part) {
        // 不需要处理
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
        // 不需要处理
    }

    // 标签信息类
    private static class TabInfo {
        IEditorReference editorReference;
        String title;
        Image image;
        boolean isActive;
    }
}
