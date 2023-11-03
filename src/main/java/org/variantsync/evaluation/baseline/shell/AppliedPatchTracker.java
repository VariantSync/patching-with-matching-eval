package org.variantsync.evaluation.baseline.shell;

import org.tinylog.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

public class AppliedPatchTracker implements Consumer<String> {
    private Path lastPatchedFile = Path.of("");
    private boolean receivedError = false;

    @Override
    public void accept(String s) {
        if (s.startsWith("patching file ")) {
            lastPatchedFile = Path.of(s.replace("patching file ", "").trim());
        } else if (s.startsWith("patch: **** write error : Success")) {
            Logger.debug("{} for file {}", s, lastPatchedFile);
            receivedError = true;
        }
    }

    public Path lastPatchTarget() {
        return lastPatchedFile;
    }

    public boolean hasReceivedError() {
        return receivedError;
    }

    public void reset() {
        this.lastPatchedFile = Path.of("");
        this.receivedError = false;
    }
}
