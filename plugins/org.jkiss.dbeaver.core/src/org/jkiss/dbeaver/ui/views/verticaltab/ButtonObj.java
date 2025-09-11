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

import org.eclipse.swt.widgets.Button;
import org.jkiss.utils.StringUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ButtonObj<T extends ButtonObj.TextObj> {
    private final Button button;
    private final AtomicReference<String> renderedText;
    private T t;

    public ButtonObj(String renderedText, Button button) {
        this.renderedText = new AtomicReference<>(renderedText);
        this.button = button;
    }


    public Button getButton() {
        return button;
    }

    public void updateT(T t) {
        if (t == null) {
            renderedText.set("?");
            return;
        }
        String text = t.text();
        if (!Objects.equals(text, renderedText.get())) {
            button.setText(text);
            renderedText.set(text);
        }
    }

    public interface TextObj {
        String text();
    }

    public boolean isSelected() {
        return this.button.getSelection();
    }

}
