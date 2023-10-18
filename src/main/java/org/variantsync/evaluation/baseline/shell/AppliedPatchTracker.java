package org.variantsync.evaluation.baseline.shell;

import org.tinylog.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

public class AppliedPatchTracker implements Consumer<String> {
    private ArrayList<Path> appliedPatches = new ArrayList<>();
    private boolean receivedError = false;

    @Override
    public void accept(String s) {
        if (s.startsWith("patching file ")) {
            appliedPatches.add(Path.of(s.replace("patching file ", "").trim()));
        } else if (s.startsWith("patch: **** write error :")) {
            Logger.debug("{} for file {}", s, appliedPatches.get(appliedPatches.size()-1));
            receivedError = true;
        }
    }

    public Path lastPatchTarget() {
        return appliedPatches.get(appliedPatches.size()-1);
    }

    public boolean hasReceivedError() {
        return receivedError;
    }

    public void reset() {
        this.appliedPatches = new ArrayList<>();
        this.receivedError = false;
    }
}
