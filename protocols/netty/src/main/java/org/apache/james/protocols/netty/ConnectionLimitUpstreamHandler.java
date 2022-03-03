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
package org.apache.james.protocols.netty;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/**
 * {@link ChannelInboundHandlerAdapter} which limit the concurrent connection.
 * 
 * This handler must be used as singleton when adding it to the {@link ChannelPipeline} to work correctly
 *
 */
@ChannelHandler.Sharable
public class ConnectionLimitUpstreamHandler extends ChannelInboundHandlerAdapter {
    private static final String CONNECTION_LIMIT_HANDLER = "connectionLimitHandler";

    public static void addToPipeline(ChannelPipeline pipeline, int connPerIP) {
        if (connPerIP > 0) {
            pipeline.addLast(CONNECTION_LIMIT_HANDLER, new ConnectionLimitUpstreamHandler(connPerIP));
        }
    }

    private final AtomicInteger connections = new AtomicInteger(0);
    private final int maxConnections;
    
    private ConnectionLimitUpstreamHandler(int maxConnections) {
        Preconditions.checkArgument(maxConnections > 0);
        this.maxConnections = maxConnections;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int currentCount = connections.incrementAndGet();

        if (currentCount > maxConnections) {
            ctx.close();
        }
        
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (maxConnections > 0) {
            connections.decrementAndGet();
        }
        super.channelInactive(ctx);
    }
}
