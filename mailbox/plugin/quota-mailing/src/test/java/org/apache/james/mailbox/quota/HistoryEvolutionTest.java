/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.quota;

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class HistoryEvolutionTest {

    /*
    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(HistoryEvolution.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void isModifiedShouldReturnFalseWhenNoChange() {
        assertThat(
            HistoryEvolution.noChanges(_75)
                .isChange())
            .isFalse();
    }

    @Test
    public void isModifiedShouldReturnTrueWhenLowerThresholdReached() {
        assertThat(
            HistoryEvolution.lowerThresholdReached(_75)
                .isChange())
            .isTrue();
    }

    @Test
    public void isModifiedShouldReturnTrueWhenHigherThresholdAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(_75, HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePriod)
                .isChange())
            .isTrue();
    }

    @Test
    public void isModifiedShouldReturnTrueWhenHigherThresholdReachedNotAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(_75, HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod)
                .isChange())
            .isTrue();
    }

    @Test
    public void currentThresholdNotRecentlyReachedShouldReturnFalseWhenNoChange() {
        assertThat(
            HistoryEvolution.noChanges(_75)
                .currentThresholdNotRecentlyReached())
            .isFalse();
    }

    @Test
    public void currentThresholdNotRecentlyReachedShouldReturnFalseWhenLowerThresholdReached() {
        assertThat(
            HistoryEvolution.lowerThresholdReached(_75)
                .currentThresholdNotRecentlyReached())
            .isFalse();
    }

    @Test
    public void currentThresholdNotRecentlyReachedShouldReturnFalseWhenHigherThresholdReachedAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(_75, HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePriod)
                .currentThresholdNotRecentlyReached())
            .isFalse();
    }

    @Test
    public void currentThresholdNotRecentlyReachedShouldReturnTrueWhenHigherThresholdReachedNotAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(_75, HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod)
                .currentThresholdNotRecentlyReached())
            .isTrue();
    }
    */
}