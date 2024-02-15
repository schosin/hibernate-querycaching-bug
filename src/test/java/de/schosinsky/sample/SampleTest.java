/*
 * Copyright 2014 - 2024 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schosinsky.sample;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.hibernate.query.sqm.internal.ConcreteSqmSelectQueryPlan;
import org.hibernate.query.sqm.internal.QuerySqmImpl;
import org.junit.Assert;
import org.junit.Test;

import com.blazebit.persistence.querydsl.BlazeJPAQuery;
import com.querydsl.core.Tuple;

import de.schosinsky.model.QPerson;

public class SampleTest extends AbstractSampleTest {

    private static final QPerson PERSON = QPerson.person;

    /**
     * Race condition in {@link ConcreteSqmSelectQueryPlan#withCacheableSqmInterpretation}.
     * The blaze part doesn't seem to be important here, I just happened to start with this instead of the hibernate one.
     * 
     * See https://hibernate.zulipchat.com/#narrow/stream/132096-hibernate-user/topic/6.2E4.2E1.2C.20.22limit.20null.2C20.22.20for.20MySQL.20generated for discussion.
     * 
     * Steps to reproduce:
     * 
     * Add breakpoint in {@link QuerySqmImpl#resolveSelectQueryPlan} (line 610)
     * Add breakpoint before double-lock check in {@link ConcreteSqmSelectQueryPlan#withCacheableSqmInterpretation} (line 317)
     * Add breakpoint within synchronized block of double-lock check (line 323)
     * Debug raceConditionTest
     * Wait for both threads to halt in QuerySqmImpl#resolveSelectQueryPlan
     * Let one thread run until it halts at second breakpoint (before double-lock check)
     * Let the other thread run until it halts at second breakpoint (before double-lock check)
     * Make sure both are in the same instance of ConcreteSqmSelectQueryPlan
     * Let the thread with offset 20 enter synchronized block (third breakpoint): executionContext.getQueryOptions().getLimit().getFirstRow() == 20
     * Let the other thread wait on the synchronized block
     * Run the offset 20 thread
     * Run the other thread
     * 
     * Result will be a java.sql.SQLSyntaxErrorException:
     * Caused by: java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near 'null,20' at line 1
     */
    @Test
    public void raceConditionTest() {
        transactional((em, factory) -> {
            var executor = Executors.newFixedThreadPool(2);

            try {
                var f1 = executor.submit(() -> {
                    // order of things to match code that causes the bug
                    new BlazeJPAQuery<Tuple>(em, factory).from(PERSON)
                            .select(PERSON.id, PERSON.name)
                            .orderBy(PERSON.name.asc())
                            .where(PERSON.id.gt(0))
                            .limit(20L)
                            .offset(20L)
                            .fetch();
                });

                var f2 = executor.submit(() -> {
                    // order of things to match code that causes the bug
                    new BlazeJPAQuery<Tuple>(em, factory).from(PERSON)
                            .select(PERSON.id, PERSON.name)
                            .orderBy(PERSON.name.asc())
                            .where(PERSON.id.gt(0))
                            .limit(20L)
                            .offset(0L)
                            .fetch();
                });

                try {
                    f1.get();
                } catch (ExecutionException ex) {
                    ex.printStackTrace();
                    Assert.fail("Thread 1: " + ex.getMessage());
                }

                try {
                    f2.get();
                } catch (ExecutionException ex) {
                    ex.printStackTrace();
                    Assert.fail("Thread 2: " + ex.getMessage());
                }
            } catch (InterruptedException ex) {
                Assert.fail("interrupted");
            } finally {
                executor.shutdown();
            }
        });
    }
}
