/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.graph.internal;

import ai.grakn.Grakn;
import ai.grakn.GraknGraph;
import ai.grakn.GraknTxType;
import ai.grakn.exception.GraphOperationException;
import ai.grakn.exception.InvalidGraphException;
import ai.grakn.util.ErrorMessage;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GraknTinkerGraphTest extends GraphTestBase{

    @Test
    public void whenAddingMultipleConceptToTinkerGraph_EnsureGraphIsMutatedDirectlyNotViaTransaction() throws ExecutionException, InterruptedException {
        Set<Future> futures = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(10);

        graknGraph.putEntityType("Thing");
        graknGraph.commit();

        for(int i = 0; i < 20; i ++){
            futures.add(pool.submit(this::addRandomEntity));
        }

        for (Future future : futures) {
            future.get();
        }

        graknGraph = (AbstractGraknGraph<?>) Grakn.session(Grakn.IN_MEMORY, graknGraph.getKeyspace()).open(GraknTxType.WRITE);
        assertEquals(20, graknGraph.getEntityType("Thing").instances().count());
    }
    private synchronized void addRandomEntity(){
        try(GraknGraph graph = Grakn.session(Grakn.IN_MEMORY, graknGraph.getKeyspace()).open(GraknTxType.WRITE)){
            graph.getEntityType("Thing").addEntity();
            graph.commit();
        }
    }

    @Test
    public void whenClearingGraph_EnsureGraphIsClosedAndRealodedWhenNextOpening(){
        graknGraph.putEntityType("entity type");
        assertNotNull(graknGraph.getEntityType("entity type"));
        graknGraph.admin().delete();
        assertTrue(graknGraph.isClosed());
        graknGraph = (AbstractGraknGraph) Grakn.session(Grakn.IN_MEMORY, graknGraph.getKeyspace()).open(GraknTxType.WRITE);
        assertNull(graknGraph.getEntityType("entity type"));
        assertNotNull(graknGraph.getMetaEntityType());
    }

    @Test
    public void whenMutatingClosedGraph_Throw() throws InvalidGraphException {
        AbstractGraknGraph graph = (AbstractGraknGraph) Grakn.session(Grakn.IN_MEMORY, "new graph").open(GraknTxType.WRITE);
        graph.close();

        expectedException.expect(GraphOperationException.class);
        expectedException.expectMessage(ErrorMessage.GRAPH_CLOSED_ON_ACTION.getMessage("closed", graph.getKeyspace()));

        graph.putEntityType("thing");
    }
}