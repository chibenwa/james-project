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

import org.apache.james.lifecycle.api.LifecycleUtil;

import com.google.common.base.Preconditions;

public class ReferenceCount<Id, ExceptionId extends Exception> {

    public interface CloningOperation<Id, ExceptionId extends Exception> {
        Id clone(Id clonee) throws ExceptionId;
    }

    private Id reference;
    private CloningOperation<Id, ExceptionId> cloneOperation;
    private int count;

    public ReferenceCount(Id reference, CloningOperation<Id, ExceptionId> cloneOperation) {
        this.reference = reference;
        this.cloneOperation = cloneOperation;
        this.count = 1;
    }

    public synchronized void incrementReferenceCount() {
        count++;
    }

    public synchronized void decrementReferenceCount() {
        Preconditions.checkState(count > 0, "Can not decrement a reference that is not superior to 0");
        count--;
        if (count == 0) {
            LifecycleUtil.dispose(reference);
            reference = null;
        }
    }

    public synchronized int getReferenceCount() {
        return count;
    }

    public synchronized Id getWrapped() {
        Preconditions.checkState(count > 0, "Attend to read content of the ReferenceCount while reference should not be tracked");
        return reference;
    }

    public synchronized ReferenceCount<Id, ExceptionId> getNewReferenceForWriting() throws ExceptionId {
        Preconditions.checkState(count > 0, "Attend to write content of the ReferenceCount while reference should not be tracked");
        if (count > 1) {
            ReferenceCount<Id, ExceptionId> result = new ReferenceCount<Id, ExceptionId>(cloneOperation.clone(reference), cloneOperation);
            decrementReferenceCount();
            return result;
        } else {
            return this;
        }
    }

}
