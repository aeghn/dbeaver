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
package org.jkiss.dbeaver.ui.views.verticaltab;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorReference;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ui.UIUtils;

public class TabItem {
    public static final String TAB_INFO_KEY = "tabInfo";

    private String id;

    private IEditorReference editorReference;
    private Composite tabComposite;

    private boolean isActive;
    private boolean isPinned;

    private Database database;

    public Composite getTabComposite() {
        return tabComposite;
    }

    @Nullable
    public static TabItem create(@Nullable IEditorReference editorRef, Composite parent) {
        if (editorRef == null) {
            return null;
        }

        TabItem tabItem = new TabItem();
        tabItem.id = editorRef.getId();
        tabItem.database = Database.from(editorRef, null);
        tabItem.editorReference = editorRef;

        Composite tabComposite = new Composite(parent, SWT.NONE);
        tabItem.tabComposite = tabComposite;
        tabComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        tabComposite.setData(TAB_INFO_KEY, tabItem);

        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 3;
        layout.marginHeight = 1;
        layout.horizontalSpacing = 3;
        tabComposite.setLayout(layout);

        createIconLabel(tabComposite, tabItem);
        createTitleLabel(tabComposite, tabItem);
        createCloseButton(tabComposite, tabItem);

        tabItem.addTabSelectionListener(tabComposite, tabItem);
        tabItem.updateStyle();

        return tabItem;
    }

    /**
     * 创建图标标签
     */
    private static Label createIconLabel(Composite parent, TabItem tabItem) {
        Label iconLabel = new Label(parent, SWT.NONE);
        if (tabItem.getEditorReference().getTitleImage() != null) {
            iconLabel.setImage(tabItem.getEditorReference().getTitleImage());
        }
        iconLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // 图标也可点击选择标签
        iconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                tabItem.onSelect();
            }
        });

        return iconLabel;
    }

    private static Label createTitleLabel(Composite parent, TabItem tabItem) {
        Label titleLabel = new Label(parent, SWT.NONE);
        titleLabel.setText(tabItem.getEditorReference().getTitle());
        titleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 标题也可点击选择标签
        titleLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                tabItem.onSelect();
            }
        });

        return titleLabel;
    }

    private static Button createCloseButton(Composite parent, TabItem tabItem) {
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

        closeButton.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            if (tabItem.getEditorReference() != null) {

            }
        }));

        return closeButton;
    }

    @Nonnull
    public IEditorReference getEditorReference() {
        return editorReference;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public Database getDatabase() {
        return database;
    }

    private void updateStyle() {
        if (tabComposite.isDisposed()) {
            return;
        }

        if (this.isActive()) {
            for (Control child : tabComposite.getChildren()) {
                if (child.isDisposed()) continue;

                child.setFont(UIUtils.makeBoldFont(child.getFont()));
            }
        } else {
            for (Control child : tabComposite.getChildren()) {
                if (child.isDisposed()) continue;
            }
        }
    }

    private void addTabSelectionListener(Composite tabComposite, TabItem tabItem) {
        MouseAdapter selectionAdapter = new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (e.button == 1) { // 左键点击
                    onSelect();
                }
            }
        };

        tabComposite.addMouseListener(selectionAdapter);
    }

    private void onSelect() {
        this.isActive = true;

        try {
            this.getEditorReference().getPage().activate(this.getEditorReference().getPart(false));
        } catch (Exception e) {
            Log.getLog(TabItem.class.getCanonicalName()).error("Error activating editor: ", e);
        }
    }

    public void update() {

    }
}
