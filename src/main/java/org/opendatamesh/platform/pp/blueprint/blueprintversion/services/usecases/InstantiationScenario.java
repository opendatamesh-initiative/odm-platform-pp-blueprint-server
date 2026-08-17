package org.opendatamesh.platform.pp.blueprint.blueprintversion.services.usecases;

/**
 * Instantiation layout derived from {@code instantiation.strategy} and presence of {@code composition}.
 * Shared by instantiate and update-data-product use cases.
 * <ul>
 *   <li>{@link #MONOREPO_NO_COMPOSITION} — 1→1</li>
 *   <li>{@link #MONOREPO_WITH_COMPOSITION} — N→1</li>
 *   <li>{@link #POLYREPO_NO_COMPOSITION} — 1→N</li>
 *   <li>{@link #POLYREPO_WITH_COMPOSITION} — N→N</li>
 * </ul>
 */
public enum InstantiationScenario {
    MONOREPO_NO_COMPOSITION,
    MONOREPO_WITH_COMPOSITION,
    POLYREPO_NO_COMPOSITION,
    POLYREPO_WITH_COMPOSITION
}
