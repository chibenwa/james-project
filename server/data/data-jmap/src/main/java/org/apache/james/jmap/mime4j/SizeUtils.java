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

package org.apache.james.jmap.mime4j;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MessageImpl;

import com.google.common.io.CountingOutputStream;

public class SizeUtils {
    public static long sizeOf(Entity entity) throws IOException {
        if (entity instanceof BodyPart) {
            BodyPart bodyPart = (BodyPart) entity;

            return sizeOf(bodyPart.getBody());
        }
        if (entity instanceof MessageImpl) {
            MessageImpl bodyPart = (MessageImpl) entity;
            CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
            DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
            defaultMessageWriter.writeHeader(bodyPart.getHeader(), countingOutputStream);

            return sizeOf(bodyPart.getBody());
        }
        CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
        DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
        defaultMessageWriter.writeEntity(entity, countingOutputStream);
        return countingOutputStream.getCount();
    }

    public static long sizeOf(Body body) throws IOException {
        if (body instanceof FakeBinaryBody) {
            return ((FakeBinaryBody) body).getSize();
        }
        if (body instanceof SingleBody) {
            return ((SingleBody) body).size();
        }
        try {
            CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
            DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
            defaultMessageWriter.writeBody(body, countingOutputStream);
            return countingOutputStream.getCount();
        } catch (Exception e) {
            return 0;
        }
    }

    /*
      private def size(entity: Entity): Try[Size] = {
    println(entity.getClass)
    if (body.)
    entity.getBody match {
      case body: BodyPart => {
        val countingOutputStream: CountingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream())
        val writer = new DefaultMessageWriter
        writer.writeHeader(body.getHeader, countingOutputStream)
        refineSize(countingOutputStream.getCount + sizeOfBody(body.getBody))
      }
      case body =>
        val countingOutputStream: CountingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream())
        val writer = new DefaultMessageWriter
        writer.writeBody(body, countingOutputStream)
        refineSize(countingOutputStream.getCount)
    }
  }
  private def sizeOfBody(body: org.apache.james.mime4j.dom.Body): Long = {
    body match {
      case body2 if body.getClass.equals(classOf[FakeBinaryBody]) => body2.asInstanceOf[FakeBinaryBody].getSize()
      case body2 if body.getClass.equals(classOf[SingleBody]) => body2.asInstanceOf[SingleBody].size()
      case body2 =>
        val countingOutputStream: CountingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream())
        val writer = new DefaultMessageWriter
        writer.writeBody(body, countingOutputStream)
        countingOutputStream.getCount
    }
  }
     */
}
