/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.widgets;

import java.util.ArrayList;
import java.util.List;

public abstract class WMultiLabel extends WLabel {
    protected List<String> lines = new ArrayList<>(2);

    protected double maxWidth;

    public WMultiLabel(String text, boolean title, double maxWidth) {
        super(text, title);

        this.maxWidth = maxWidth;
    }

    @Override
    protected void onCalculateSize() {
        lines.clear();

        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();

        double spaceWidth = theme.textWidth(" ", 1, title);
        double maxWidth = theme.scale(this.maxWidth);

        double lineWidth = 0;
        double maxLineWidth = 0;

        int iInLine = 0;

        for (int i = 0; i < words.length; i++) {
            double wordWidth = theme.textWidth(words[i], words[i].length(), title);

            double toAdd = wordWidth;
            if (iInLine > 0) toAdd += spaceWidth;

            if (lineWidth + toAdd > maxWidth) {
                // A single word wider than maxWidth all on its own doesn't fit no matter how many
                // times we retry it on a fresh line - lineWidth is already 0 here, so the retry
                // below would hit this exact same branch forever, growing `lines` by one empty
                // string every iteration until the heap runs out (this is a real crash this
                // caused - see WMultiLabel.java's history). Place it on its own line as-is instead
                // and move on, even though it'll overflow maxWidth slightly - still bounded,
                // unlike looping forever.
                if (iInLine == 0) {
                    lines.add(words[i]);
                    maxLineWidth = Math.max(maxLineWidth, wordWidth);
                    continue;
                }

                lines.add(sb.toString());
                sb.setLength(0);

                lineWidth = 0;
                iInLine = 0;

                i--;
            }
            else {
                if (iInLine > 0) {
                    sb.append(' ');
                    lineWidth += spaceWidth;
                }

                sb.append(words[i]);
                lineWidth += wordWidth;

                maxLineWidth = Math.max(maxLineWidth, lineWidth);
                iInLine++;
            }
        }

        if (!sb.isEmpty()) lines.add(sb.toString());

        width = maxLineWidth;
        height = theme.textHeight(title) * lines.size();
    }

    @Override
    public void set(String text) {
        if (!text.equals(this.text)) invalidate();

        this.text = text;
    }
}
