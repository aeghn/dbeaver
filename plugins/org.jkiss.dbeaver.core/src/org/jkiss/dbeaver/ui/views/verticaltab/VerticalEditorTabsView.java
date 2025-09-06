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
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.struct.DBSInstance;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.actions.datasource.DataSourceHandler;
import org.jkiss.dbeaver.ui.editors.DatabaseEditorInput;
import org.jkiss.dbeaver.ui.editors.EditorUtils;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import org.jkiss.dbeaver.ui.internal.UINavigatorMessages;

import java.util.ArrayList;
import java.util.List;

/**
 * VerticalEditorTabsView
 */
public class VerticalEditorTabsView extends ViewPart implements IPartListener {
    public static final String VIEW_ID = "org.jkiss.dbeaver.ui.editors.verticaltabs.VerticalEditorTabsView";

    private Composite controlArea;
    private Button currentDatabaseCheck;
    private Button currentSchemaCheck;
    private TableViewer tabsViewer;
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
        tabsViewer = new TableViewer(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
        Table table = tabsViewer.getTable();
        table.setLayoutData(new GridData(GridData.FILL_BOTH));
        table.setHeaderVisible(false);
        table.setLinesVisible(false);

        // 创建列：图标、标题、关闭按钮
        TableViewerColumn iconColumn = new TableViewerColumn(tabsViewer, SWT.LEFT);
        iconColumn.getColumn().setWidth(20);

        TableViewerColumn titleColumn = new TableViewerColumn(tabsViewer, SWT.LEFT);
        titleColumn.getColumn().setWidth(200);

        TableViewerColumn closeColumn = new TableViewerColumn(tabsViewer, SWT.RIGHT);
        closeColumn.getColumn().setWidth(20);

        tabsViewer.setContentProvider(new IStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                if (inputElement instanceof List) {
                    return ((List<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });

        tabsViewer.setLabelProvider(new CellLabelProvider() {
            @Override
            public void update(ViewerCell cell) {
                if (cell.getElement() instanceof TabInfo) {
                    TabInfo tabInfo = (TabInfo) cell.getElement();
                    int columnIndex = cell.getColumnIndex();

                    if (columnIndex == 0) {
                        // 图标列
                        if (tabInfo.image != null) {
                            cell.setImage(tabInfo.image);
                        }
                    } else if (columnIndex == 1) {
                        // 标题列
                        cell.setText(tabInfo.title);
                        // 高亮当前标签页
                        if (tabInfo.isActive) {
                            cell.setBackground(null);
                            // UIUtils.getSharedTextColors().getColor(
                            // UIUtils.getActiveWorkbenchWindow().getShell().getDisplay(),
                            //                                    new Color(220, 230, 250))
                        } else {
                            cell.setBackground(null);
                        }
                    } else if (columnIndex == 2) {
                        // 关闭按钮列
                        cell.setText("×");
                    }
                }
            }
        });

        // 添加选择监听器
        tabsViewer.addSelectionChangedListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            if (!selection.isEmpty()) {
                TabInfo tabInfo = (TabInfo) selection.getFirstElement();
                if (tabInfo.editorReference != null) {
                    workbenchPage.activate(tabInfo.editorReference.getPart(true));
                }
            }
        });

        // 添加双击关闭功能
        tabsViewer.addDoubleClickListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            if (!selection.isEmpty()) {
                TabInfo tabInfo = (TabInfo) selection.getFirstElement();
                if (tabInfo.editorReference != null) {
                    workbenchPage.closeEditor(tabInfo.editorReference.getEditor(false), true);
                }
            }
        });

        // 添加上下文菜单
        createContextMenu();
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
                // 应用过滤器
                if ((filterCurrentDatabase || filterCurrentSchema) && editor instanceof IDatabaseEditorInput edei) {
                    DBCExecutionContext context = edei.getExecutionContext();
                    if (context != null && currentExecutionContext != null) {
                        // 检查数据库过滤
                        if (filterCurrentDatabase) {
                            DBSInstance currentInstance = currentExecutionContext.getDataSource().getDefaultInstance();
                            DBSInstance tabInstance = context.getDataSource().getDefaultInstance();
                            if (currentInstance != tabInstance) {
                                continue;
                            }
                        }

                        // 检查schema过滤
//                        if (filterCurrentSchema) {
//                            DBSObject currentDefaultObject = currentExecutionContext.getDefaultCatalog();
//                            if (currentDefaultObject == null) {
//                                currentDefaultObject = currentExecutionContext.getDefaultSchema();
//                            }
//                            DBSObject tabDefaultObject = context.getDefaultCatalog();
//                            if (tabDefaultObject == null) {
//                                tabDefaultObject = context.getDefaultSchema();
//                            }
//                            if (currentDefaultObject != tabDefaultObject) {
//                                continue;
//                            }
//                        }
                    }
                }

                TabInfo tabInfo = new TabInfo();
                tabInfo.editorReference = editorRef;
                tabInfo.title = editorRef.getTitle();
                tabInfo.isActive = editorRef.equals(workbenchPage.getActiveEditor());

                // 获取编辑器图标
                ImageDescriptor imageDesc = null;
                try {
                    imageDesc = editorRef.getEditorInput().getImageDescriptor();
                    if (imageDesc != null) {
                        tabInfo.image = imageDesc.createImage();
                    }


                } catch (PartInitException ignored) {

                }


                tabs.add(tabInfo);
            }
        }

        tabsViewer.setInput(tabs);
        tabsViewer.refresh();
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
