package com.coremedia.iso.gui;

import com.coremedia.iso.boxes.Box;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/**
 * Created by IntelliJ IDEA.
 * User: sannies
 * Date: 7/11/11
 * Time: 3:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class BoxNodeRenderer extends DefaultTreeCellRenderer {
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected,
                                                  boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
        if (value instanceof Box) {
            setText(((Box) value).getType());
        } else {
            setText(value.toString());
        }
        return this;
    }
}

