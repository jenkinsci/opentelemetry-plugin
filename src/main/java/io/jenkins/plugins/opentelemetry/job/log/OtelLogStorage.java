package io.jenkins.plugins.opentelemetry.job.log;

class OtelLogStorage implements LogStorage {

        final BuildInfo buildInfo;

        public OtelLogStorage(@Nonnull BuildInfo buildInfo) {
            this.buildInfo= buildInfo;
        }

        @Nonnull
        @Override
        public BuildListener overallListener() {
            return new AbstractOtelLogSender.MasterOtelLogSender(buildInfo);
        }

        @Nonnull
        @Override
        public TaskListener nodeListener(@Nonnull FlowNode node) {
            return new AbstractOtelLogSender.NodeOtelLogSender(buildInfo, node);
        }

        @Nonnull
        @Override
        public AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(@Nonnull FlowExecutionOwner.Executable build, boolean complete) {
            ByteBuffer buffer = new ByteBuffer();
            try {
                buffer.write("FIXME".getBytes(StandardCharsets.UTF_8));// FIXME
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new AnnotatedLargeText<>(buffer, StandardCharsets.UTF_8, true, build);
        }

        @Nonnull
        @Override
        public AnnotatedLargeText<FlowNode> stepLog(@Nonnull FlowNode node, boolean complete) {
            ByteBuffer buffer = new ByteBuffer();
            try {
                buffer.write("FIXME".getBytes(StandardCharsets.UTF_8));// FIXME
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new AnnotatedLargeText<>(buffer, StandardCharsets.UTF_8, true, node);
        }

        @Override
        public String toString() {
            return "OtelLogStorage{" +
                "buildInfo=" + buildInfo +
                '}';
        }
    }