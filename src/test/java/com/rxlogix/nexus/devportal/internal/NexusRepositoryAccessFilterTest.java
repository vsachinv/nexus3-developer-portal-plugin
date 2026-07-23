package com.rxlogix.nexus.devportal.internal;

import org.junit.jupiter.api.Test;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.security.RepositoryPermissionChecker;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusRepositoryAccessFilterTest {

    @Test
    void failsClosedWhenPermissionCheckerUnavailable() {
        NexusBeanLocator locator = mock(NexusBeanLocator.class);
        when(locator.lookup(RepositoryPermissionChecker.class)).thenReturn(Optional.empty());

        NexusRepositoryAccessFilter filter = new NexusRepositoryAccessFilter(locator);

        // A non-empty input with no resolvable checker must return nothing, never the input.
        List<Repository> input = List.of(mock(Repository.class));
        assertThat(filter.readable(input)).isEmpty();
    }

    @Test
    void emptyInputShortCircuits() {
        NexusBeanLocator locator = mock(NexusBeanLocator.class);
        NexusRepositoryAccessFilter filter = new NexusRepositoryAccessFilter(locator);

        assertThat(filter.readable(List.of())).isEmpty();
    }
}
