package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases.evaluateprotectedresources;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic close of every retained expected tree.
 * Scenarios trace to {@code spdd/prompt/BDMD-5124-202608210930-[Feat]-service-protected-resources-integrity-policy-adapter.md}.
 */
class TargetWorkingTreesTest {

    /**
     * Feature: Lasting protected-resources integrity
     *
     * Scenario: Expected trees close in insertion order and every tree is attempted
     *   Given several keyed working trees in a non-sorted insertion order
     *   And the second tree throws on close
     *   When the holder is closed
     *   Then trees close in insertion order
     *   And later trees are still closed
     *   And the first close failure is rethrown
     */
    @Test
    void closeFollowsInsertionOrderAndContinuesAfterFailure() {
        List<String> closed = new ArrayList<>();
        Map<String, WorkingTree> trees = new LinkedHashMap<>();
        trees.put("gamma", recordingTree("gamma", closed, null));
        trees.put("alpha", recordingTree("alpha", closed, new IllegalStateException("close failed")));
        trees.put("beta", recordingTree("beta", closed, null));

        TargetWorkingTrees holder = new TargetWorkingTrees(trees);
        assertThat(holder.keys()).containsExactly("gamma", "alpha", "beta");

        assertThatThrownBy(holder::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("close failed");
        assertThat(closed).containsExactly("gamma", "alpha", "beta");
    }

    private static WorkingTree recordingTree(String key, List<String> closed, RuntimeException failure) {
        return new WorkingTree() {
            @Override
            public Path path() {
                return Path.of(key);
            }

            @Override
            public void close() {
                closed.add(key);
                if (failure != null) {
                    throw failure;
                }
            }
        };
    }
}
