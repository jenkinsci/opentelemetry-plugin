package io.jenkins.plugins.opentelemetry.job.log;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.io.PrintStream;

public class MergedPrintStream extends PrintStream {
    final PrintStream primary;
    final PrintStream secondary;

    public MergedPrintStream(@NonNull PrintStream primary, @NonNull PrintStream secondary) throws IOException {
        super(primary, false, "UTF-8");
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public void flush() {
        super.flush();
        secondary.flush();
    }

    @Override
    public void close() {
        super.close();
        secondary.close();
    }

    @Override
    public boolean checkError() {
        return super.checkError() && secondary.checkError();
    }


    @Override
    public void write(int b) {
        super.write(b);
        secondary.write(b);
    }

    @Override
    public void write(@NonNull byte[] buf, int off, int len) {
        super.write(buf, off, len);
        secondary.write(buf, off, len);
    }
}