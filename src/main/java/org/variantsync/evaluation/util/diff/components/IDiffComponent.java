package org.variantsync.evaluation.util.diff.components;

import java.util.List;

/**
 * An IDiffComponent is a component of the difference between two files (e.g., the difference between two files or the
 * difference between two blocks of text (i.e., a hunk)). The interface is used to convert the object representation of a
 * component back into text that can be provided as input for UNIX patch.
 */
public interface IDiffComponent {
    /**
     * Parse this component back into a list of lines that can be added to a diff file meant as input for unix patch
     *
     * @return the lines of the part of the diff that this component represents
     */
    List<String> toLines();

    int changeCount();
}