package org.XXXX-1.evaluation.util.diff.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class PartiallyEquals {

    static <T> boolean subsetPartiallyEquals(List<T> left, List<T> right, BiFunction<T, T, Boolean> compare) {
        List<T> othersElementsModifiable = new ArrayList<>(right);

        for (T leftElement : left) {
            int deletionIndex = -1;
            for (int i = 0; i < othersElementsModifiable.size(); i++) {
                if (compare.apply(leftElement, othersElementsModifiable.get(i))) {
                    deletionIndex = i;
                    // Exit on the first partially equal diff
                    break;
                }
            }
            if (deletionIndex == -1) {
                // If no partially equal diff is found, the diffs are not equal
                return false;
            } else {
                othersElementsModifiable.remove(deletionIndex);
            }
        }
        return true;
    }
}
