/*
 * DBeaver - Universal Database Manager
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
package org.jkiss.dbeaver.ui.views.verticaltab;/*
 * DBeaver - Universal Database Manager
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

import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import org.jkiss.dbeaver.ui.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class VerticalEditorTabsView extends ViewPart {
    public static final String VIEW_ID = "org.jkiss.dbeaver.ui.editors.vertical";

    private ListViewer viewer;
    private Text filterText;
    private Combo filterTypeCombo;
    private Action refreshAction;

    private final List<EditorInfo> editorItems = new ArrayList<>();

    public VerticalEditorTabsView() {
        super();
    }

    @Override
    public void createPartControl(Composite parent) {
        // 创建工具栏
        createActions();
        contributeToActionBars();

        // 创建过滤区域
        Composite filterComposite = new Composite(parent, SWT.NONE);
        filterComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout filterLayout = new GridLayout(3, false);
        filterLayout.marginHeight = 0;
        filterLayout.marginWidth = 0;
        filterComposite.setLayout(filterLayout);

        filterTypeCombo = new Combo(filterComposite, SWT.DROP_DOWN | SWT.READ_ONLY);
        filterTypeCombo.setItems("Database", "Schema");
        filterTypeCombo.select(0);
        filterTypeCombo.addModifyListener(e -> viewer.refresh());

        filterText = new Text(filterComposite, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH);
        filterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        filterText.setMessage("Filter editors");
        filterText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                viewer.refresh();
            }
        });

        // 创建查看器
        viewer = new ListViewer(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.MULTI);
        viewer.getList().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        viewer.setContentProvider(new EditorTabsContentProvider());
        viewer.setLabelProvider(new EditorTabsLabelProvider());
        viewer.setInput(PlatformUI.getWorkbench());

        // 添加选择监听器
        viewer.addSelectionChangedListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            if (!selection.isEmpty()) {
                Object selectedItem = selection.getFirstElement();
                if (selectedItem instanceof EditorInfo editorInfo) {
                    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                    if (page != null && editorInfo.editor != null) {
                        page.activate(editorInfo.editor);
                    }
                }
            }
        });

        // 监听编辑器变化
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().addPartListener(new IPartListener2() {
            @Override
            public void partActivated(IWorkbenchPartReference partRef) {
                if (partRef.getPart(false) instanceof IEditorPart) {
                    refreshViewer();
                }
            }

            @Override
            public void partBroughtToTop(IWorkbenchPartReference partRef) {
            }

            @Override
            public void partClosed(IWorkbenchPartReference partRef) {
                if (partRef.getPart(false) instanceof IEditorPart) {
                    refreshViewer();
                }
            }

            @Override
            public void partDeactivated(IWorkbenchPartReference partRef) {
            }

            @Override
            public void partHidden(IWorkbenchPartReference partRef) {
            }

            @Override
            public void partInputChanged(IWorkbenchPartReference partRef) {
            }

            @Override
            public void partOpened(IWorkbenchPartReference partRef) {
                if (partRef.getPart(false) instanceof IEditorPart) {
                    refreshViewer();
                }
            }

            @Override
            public void partVisible(IWorkbenchPartReference partRef) {
            }
        });

        refreshViewer();
    }

    private void createActions() {
        refreshAction = new Action("Refresh", UIUtils.getShardImageDescriptor(ISharedImages.IMG_ELCL_SYNCED)) {
            @Override
            public void run() {
                refreshViewer();
            }
        };
        refreshAction.setToolTipText("Refresh editor list");
    }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager toolBarManager = bars.getToolBarManager();
        toolBarManager.add(refreshAction);
    }

    private void refreshViewer() {
        if (viewer != null && !viewer.getControl().isDisposed()) {
            editorItems.clear();

            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null) {
                IWorkbenchPage page = window.getActivePage();
                if (page != null) {
                    IEditorReference[] editorRefs = page.getEditorReferences();
                    for (IEditorReference ref : editorRefs) {
                        try {
                            IEditorPart editor = (IEditorPart) ref.getPart(false);
                            if (editor != null) {
                                EditorInfo editorInfo = new EditorInfo();
                                editorInfo.editor = editor;
                                editorInfo.title = editor.getTitle();
                                editorInfo.toolTip = editor.getTitleToolTip();

                                // 获取数据库信息
                                if (editor.getEditorInput() instanceof IDatabaseEditorInput dbInput) {
                                    DBCExecutionContext context = dbInput.getExecutionContext();
                                    if (context != null) {
                                        DBPDataSource dataSource = context.getDataSource();
                                        if (dataSource != null) {
                                            editorInfo.databaseName = dataSource.getContainer().getName();

                                            // 尝试获取schema信息
                                            try {
                                                editorInfo.schemaName = context.getContextName() != null ?
                                                        context.getDataSource().getName() : null;
                                            } catch (Exception e) {
                                                // 忽略获取schema时的异常
                                            }
                                        }
                                    }
                                }

                                editorItems.add(editorInfo);
                            }
                        } catch (Exception e) {
                            // 忽略异常
                        }
                    }
                }
            }

            viewer.refresh();
        }
    }

    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    class EditorTabsContentProvider implements IStructuredContentProvider {
        @Override
        public Object[] getElements(Object inputElement) {
            String filterTextValue = filterText.getText().trim().toLowerCase();
            int filterTypeIndex = filterTypeCombo.getSelectionIndex();

            if (filterTextValue.isEmpty()) {
                return editorItems.toArray();
            }

            List<EditorInfo> filteredItems = new ArrayList<>();
            for (EditorInfo item : editorItems) {
                boolean matches = false;

                if (filterTypeIndex == 0) { // Database filter
                    if (item.databaseName != null && item.databaseName.toLowerCase().contains(filterTextValue)) {
                        matches = true;
                    }
                } else if (filterTypeIndex == 1) { // Schema filter
                    if (item.schemaName != null && item.schemaName.toLowerCase().contains(filterTextValue)) {
                        matches = true;
                    }
                }

                // 如果没有特定过滤条件匹配，使用标题过滤
                if (!matches && item.title.toLowerCase().contains(filterTextValue)) {
                    matches = true;
                }

                if (matches) {
                    filteredItems.add(item);
                }
            }

            return filteredItems.toArray();
        }

        @Override
        public void dispose() {
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }
    }

    static class EditorTabsLabelProvider extends LabelProvider {
        @Override
        public String getText(Object element) {
            if (element instanceof EditorInfo editorInfo) {
                return editorInfo.title;
            }
            return super.getText(element);
        }

        @Override
        public org.eclipse.swt.graphics.Image getImage(Object element) {
            if (element instanceof EditorInfo editorInfo) {
                if (editorInfo.editor != null) {
                    return editorInfo.editor.getTitleImage();
                }
            }
            return null;
        }
    }

    static class EditorInfo {
        IEditorPart editor;
        String title;
        String toolTip;
        String databaseName;
        String schemaName;
    }
}
