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

import java.util.function.BiConsumer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;

import org.junit.After;
import org.junit.Before;

import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;

public abstract class AbstractSampleTest {

    protected EntityManagerFactory emf;
    protected CriteriaBuilderFactory cbf;

    @Before
    public void init() {
        emf = Persistence.createEntityManagerFactory("default");
        CriteriaBuilderConfiguration config = Criteria.getDefault();
        cbf = config.createCriteriaBuilderFactory(emf);
    }

    protected void transactional(BiConsumer<EntityManager, CriteriaBuilderFactory> consumer) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        boolean success = false;

        var criteria = Criteria.getDefault();
        var criteriaBuilderFactory = criteria.createCriteriaBuilderFactory(emf);

        try {
            tx.begin();
            consumer.accept(em, criteriaBuilderFactory);
            success = true;
        } finally {
            try {
                if (success) {
                    tx.commit();
                } else {
                    tx.rollback();
                }
            } finally {
                em.close();
            }
        }
    }

    @After
    public void destruct() {
        emf.close();
    }
}
