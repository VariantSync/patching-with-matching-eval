package org.XXXX-1.evaluation.util.shell;

import org.tinylog.Logger;

import java.nio.file.Path;
import java.util.function.Consumer;

public class AppliedPatchTracker implements Consumer<String> {
    private Path lastPatchedFile = Path.of("");
    private int patchId = -1;
    private boolean criticalError = false;
    private boolean normalError = false;

    @Override
    public void accept(String s) {
        if (s.startsWith("patching file ")) {
            lastPatchedFile = Path.of(s.replace("patching file ", "").trim());
            patchId++;
        } else if (s.startsWith("patch: **** write error : Success")) {
            Logger.error("{} for file {} ({}. patch)", s, lastPatchedFile, patchId + 1);
            criticalError = true;
        } else if (s.startsWith("patch: **** write error :")) {
            Logger.warn("{} for file {} ({}. patch)", s, lastPatchedFile, patchId + 1);
            normalError = true;
        }
    }

    public Path lastPatchTarget() {
        return lastPatchedFile;
    }

    public boolean hasNormalError() {
        return normalError;
    }

    public boolean hasCriticalError() {
        return criticalError;
    }

    public boolean hasAnyError() {
        return criticalError || normalError;
    }

    public int getPatchId() {
        return patchId;
    }

    public void reset() {
        this.lastPatchedFile = Path.of("");
        this.criticalError = false;
        this.normalError = false;
        this.patchId = -1;
    }
}
