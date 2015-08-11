/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.integration;

import org.ehcache.config.EvictionPrioritizer;
import org.ehcache.config.EvictionVeto;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.config.persistence.PersistenceConfiguration;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.expiry.Expirations;
import org.ehcache.expiry.Expiry;
import org.ehcache.internal.persistence.DefaultLocalPersistenceService;
import org.ehcache.internal.store.disk.OffHeapDiskStore;
import org.ehcache.internal.store.heap.OnHeapStore;
import org.ehcache.internal.store.tiering.CacheStore;
import org.ehcache.internal.store.tiering.CacheStoreServiceConfiguration;
import org.ehcache.spi.ServiceLocator;
import org.ehcache.spi.cache.Store;
import org.junit.Test;

import java.io.File;
import java.io.Serializable;

import static org.ehcache.config.ResourcePoolsBuilder.newResourcePoolsBuilder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author rism
 */
public class CacheStoreFlushWhileShutdownTest {
  @Test
  public void testCacheStoreReleaseFlushesEntries() throws Exception {

    Store.Configuration<Number, String> configuration = new Store.PersistentStoreConfiguration<Number, String, String>() {
      @Override
      public String getIdentifier() {
        return "Cache";
      }

      @Override
      public boolean isPersistent() {
        return true;
      }

      @Override
      public Class getKeyType() {
        return Number.class;
      }

      @Override
      public Class getValueType() {
        return Serializable.class;
      }

      @Override
      public EvictionVeto getEvictionVeto() {
        return null;
      }

      @Override
      public EvictionPrioritizer getEvictionPrioritizer() {
        return null;
      }

      @Override
      public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
      }

      @Override
      public Expiry getExpiry() {
        return Expirations.noExpiration();
      }

      @Override
      public ResourcePools getResourcePools() {
        return newResourcePoolsBuilder().heap(10, EntryUnit.ENTRIES).disk(10, MemoryUnit.MB).build();
      }
    };

    CacheStoreServiceConfiguration serviceConfiguration = new CacheStoreServiceConfiguration();
    serviceConfiguration.authoritativeTierProvider(OffHeapDiskStore.Provider.class);
    serviceConfiguration.cachingTierProvider(OnHeapStore.Provider.class);

    ServiceLocator serviceLocator = getServiceLocator();
    serviceLocator.startAllServices();
    CacheStore.Provider cacheStoreProvider = new CacheStore.Provider();

    cacheStoreProvider.start(serviceLocator);
    Store<Number, String> cacheStore = cacheStoreProvider.createStore(configuration, serviceConfiguration);
    cacheStoreProvider.initStore(cacheStore);
    for (int i = 0; i < 100; i++) {
      cacheStore.put(i, "hello");
    }

    for(int j = 0; j < 20; j++){
      for (int i = 0; i < 20; i++) {
        cacheStore.get(i);
      }
    }

    cacheStoreProvider.releaseStore(cacheStore);
    cacheStoreProvider.stop();

    serviceLocator.stopAllServices();

    ServiceLocator serviceLocator1 = getServiceLocator();
    serviceLocator1.startAllServices();
    cacheStoreProvider.start(serviceLocator1);
    cacheStore = cacheStoreProvider.createStore(configuration, serviceConfiguration);
    cacheStoreProvider.initStore(cacheStore);

    for(int i = 0; i < 20; i++) {
      assertThat(cacheStore.get(i).hits(), is(21l));
    }
  }

  private ServiceLocator getServiceLocator() throws Exception {
    PersistenceConfiguration persistenceConfiguration = new DefaultPersistenceConfiguration(new File(System.getProperty("java.io.tmpdir")));
    DefaultLocalPersistenceService persistenceService = new DefaultLocalPersistenceService(persistenceConfiguration);
    ServiceLocator serviceLocator = new ServiceLocator();
    serviceLocator.addService(persistenceService);
    serviceLocator.addService(new OnHeapStore.Provider());
    serviceLocator.addService(new OffHeapDiskStore.Provider());
    return serviceLocator;
  }
}