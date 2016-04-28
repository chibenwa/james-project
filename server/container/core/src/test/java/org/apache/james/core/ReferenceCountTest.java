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

package org.apache.james.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.lifecycle.api.Disposable;
import org.junit.Before;
import org.junit.Test;

public class ReferenceCountTest {

    private static final ReferenceCount.CloningOperation<ReferencedObject, Exception> CLONING_OPERATION =
        new ReferenceCount.CloningOperation<ReferencedObject, Exception>() {
            @Override
            public ReferencedObject clone(ReferencedObject clonee) throws Exception {
                return new ReferencedObject(clonee.getWrappedCount() + 1);
            }
        };
    private static final int NOT_WRAPPED = 0;

    static class ReferencedObject implements Disposable {
        private final int wrappedCount;
        private boolean disposed;

        public ReferencedObject(int wrappedCount) {
            this.wrappedCount = wrappedCount;
            this.disposed = false;
        }

        public int getWrappedCount() {
            return wrappedCount;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        public boolean isDisposed() {
            return disposed;
        }
    }

    private ReferenceCount<ReferencedObject, Exception> referenceCount;
    private ReferencedObject referencedObject;

    @Before
    public void setUp() {
        referencedObject = new ReferencedObject(NOT_WRAPPED);
        referenceCount = new ReferenceCount<ReferencedObject, Exception>(referencedObject, CLONING_OPERATION);
    }

    @Test
    public void referenceShouldBeOneByDefault() {
        assertThat(referenceCount.getReferenceCount()).isEqualTo(1);
    }

    @Test
    public void incrementReferenceCountShouldIncrementReferenceCount() {
        referenceCount.incrementReferenceCount();
        assertThat(referenceCount.getReferenceCount()).isEqualTo(2);
    }

    @Test
    public void decrementReferenceCountShouldDecrementReferenceCount() {
        referenceCount.incrementReferenceCount();
        referenceCount.decrementReferenceCount();
        assertThat(referenceCount.getReferenceCount()).isEqualTo(1);
    }

    @Test
    public void decrementShouldWorkOnDefaultValue() {
        referenceCount.decrementReferenceCount();
        assertThat(referenceCount.getReferenceCount()).isEqualTo(0);
    }

    @Test
    public void whenReferenceCountDropsAtZeroReferencedObjectShouldBeDisposed() {
        referenceCount.decrementReferenceCount();
        assertThat(referencedObject.isDisposed()).isTrue();
    }

    @Test
    public void whenReferenceCountDoNotDropsAtZeroReferencedObjectShouldNotBeDisposed() {
        referenceCount.incrementReferenceCount();
        referenceCount.decrementReferenceCount();
        assertThat(referencedObject.isDisposed()).isFalse();
    }

    @Test(expected = IllegalStateException.class)
    public void referenceCountShouldNotDropsUnderZero() {
        referenceCount.decrementReferenceCount();
        referenceCount.decrementReferenceCount();
    }

    @Test(expected = IllegalStateException.class)
    public void wrappedContentShoutNotBeAccessedWhenReferenceCountDroppedAtZero() {
        referenceCount.decrementReferenceCount();
        referenceCount.getWrapped();
    }

    @Test(expected = IllegalStateException.class)
    public void newReferenceForWritingShouldNotBeGeneratedWhenCountDroppedAtZero() throws Exception {
        referenceCount.decrementReferenceCount();
        referenceCount.getNewReferenceForWriting();
    }

    @Test
    public void getNewReferenceForWritingShouldCloneTheContentWhenShared() throws Exception {
        referenceCount.incrementReferenceCount();
        ReferenceCount<ReferencedObject, Exception> referenceCountForWrites = referenceCount.getNewReferenceForWriting();
        assertThat(referenceCount.getWrapped()).isNotEqualTo(referenceCountForWrites.getWrapped());
    }

    @Test
    public void getNewReferenceForWritingShouldNotGenerateANewReferenceWhenTheReferenceIsNotShared() throws Exception {
        ReferenceCount<ReferencedObject, Exception> referenceCountForWrites = referenceCount.getNewReferenceForWriting();
        assertThat(referenceCount).isEqualTo(referenceCountForWrites);
    }

    @Test
    public void getNewReferenceForWritingShouldDecrementReferenceCountWhenShared() throws Exception {
        referenceCount.incrementReferenceCount();
        referenceCount.getNewReferenceForWriting();
        assertThat(referenceCount.getReferenceCount()).isEqualTo(1);
    }

    @Test
    public void getNewReferenceForWritingShouldWrapNewReferenceContentWhenShared() throws Exception {
        referenceCount.incrementReferenceCount();
        ReferenceCount<ReferencedObject, Exception> referenceCountForWrites = referenceCount.getNewReferenceForWriting();
        assertThat(referenceCountForWrites.getWrapped().getWrappedCount()).isEqualTo(1);
    }

    @Test
    public void getNewReferenceForWritingShouldNotWrapOldReferenceContentWhenShared() throws Exception {
        referenceCount.incrementReferenceCount();
        referenceCount.getNewReferenceForWriting();
        assertThat(referenceCount.getWrapped().getWrappedCount()).isEqualTo(NOT_WRAPPED);
    }

    @Test
    public void getNewReferenceForWritingShouldNotWrapNewReferenceContentWhenShared() throws Exception {
        ReferenceCount<ReferencedObject, Exception> referenceCountForWrites = referenceCount.getNewReferenceForWriting();
        assertThat(referenceCountForWrites.getWrapped().getWrappedCount()).isEqualTo(NOT_WRAPPED);
    }

}
