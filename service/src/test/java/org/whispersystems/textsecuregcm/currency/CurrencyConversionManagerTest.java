/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.whispersystems.textsecuregcm.entities.CurrencyConversionEntityList;
import org.whispersystems.textsecuregcm.redis.FaultTolerantRedisClusterClient;
import org.whispersystems.textsecuregcm.redis.RedisClusterExtension;

class CurrencyConversionManagerTest {

  @RegisterExtension
  static final RedisClusterExtension REDIS_CLUSTER_EXTENSION = RedisClusterExtension.builder().build();

  static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

  @Test
  void testCurrencyCalculations() throws IOException {
    FixerClient fixerClient = mock(FixerClient.class);
    CoinGeckoClient coinGeckoClient   = mock(CoinGeckoClient.class);

    when(coinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("2.35"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("0.822876"),
        "FJD", new BigDecimal("2.0577"),
        "FKP", new BigDecimal("0.743446")
    ));

    CurrencyConversionManager manager = new CurrencyConversionManager(fixerClient, coinGeckoClient, REDIS_CLUSTER_EXTENSION.getRedisCluster(),
        List.of("FOO"), EXECUTOR, Clock.systemUTC());

    manager.updateCacheIfNecessary();

    CurrencyConversionEntityList conversions = manager.getCurrencyConversions().orElseThrow();

    assertThat(conversions.getCurrencies().size()).isEqualTo(1);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().size()).isEqualTo(4);
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("2.35"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("EUR")).isEqualTo(new BigDecimal("1.9337586"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FJD")).isEqualTo(new BigDecimal("4.835595"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FKP")).isEqualTo(new BigDecimal("1.7470981"));
  }

  @Test
  void testCurrencyCalculations_noTrailingZeros() throws IOException {
    FixerClient fixerClient = mock(FixerClient.class);
    CoinGeckoClient   CoinGeckoClient   = mock(CoinGeckoClient.class);

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("1.00000"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("0.200000"),
        "FJD", new BigDecimal("3.00000"),
        "FKP", new BigDecimal("50.0000"),
        "CAD", new BigDecimal("700.000")
    ));

    CurrencyConversionManager manager = new CurrencyConversionManager(fixerClient, CoinGeckoClient, REDIS_CLUSTER_EXTENSION.getRedisCluster(),
        List.of("FOO"), EXECUTOR, Clock.systemUTC());

    manager.updateCacheIfNecessary();

    CurrencyConversionEntityList conversions = manager.getCurrencyConversions().orElseThrow();

    assertThat(conversions.getCurrencies().size()).isEqualTo(1);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().size()).isEqualTo(5);
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("1"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("EUR")).isEqualTo(new BigDecimal("0.2"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FJD")).isEqualTo(new BigDecimal("3"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FKP")).isEqualTo(new BigDecimal("50"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("CAD")).isEqualTo(new BigDecimal("700"));
  }

  @Test
  void testCurrencyCalculations_accuracy() throws IOException {
    FixerClient fixerClient = mock(FixerClient.class);
    CoinGeckoClient   CoinGeckoClient   = mock(CoinGeckoClient.class);

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("0.999999"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("1.000001"),
        "FJD", new BigDecimal("0.000001"),
        "FKP", new BigDecimal("1")
    ));

    CurrencyConversionManager manager = new CurrencyConversionManager(fixerClient, CoinGeckoClient, REDIS_CLUSTER_EXTENSION.getRedisCluster(),
        List.of("FOO"), EXECUTOR, Clock.systemUTC());

    manager.updateCacheIfNecessary();

    CurrencyConversionEntityList conversions = manager.getCurrencyConversions().orElseThrow();

    assertThat(conversions.getCurrencies().size()).isEqualTo(1);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().size()).isEqualTo(4);
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("0.999999"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("EUR")).isEqualTo(new BigDecimal("0.999999999999"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FJD")).isEqualTo(new BigDecimal("0.000000999999"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FKP")).isEqualTo(new BigDecimal("0.999999"));

  }

  @Test
  void testCurrencyCalculationsTimeoutNoRun() throws IOException {
    FixerClient fixerClient = mock(FixerClient.class);
    CoinGeckoClient   CoinGeckoClient   = mock(CoinGeckoClient.class);

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("2.35"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("0.822876"),
        "FJD", new BigDecimal("2.0577"),
        "FKP", new BigDecimal("0.743446")
    ));

    CurrencyConversionManager manager = new CurrencyConversionManager(fixerClient, CoinGeckoClient, REDIS_CLUSTER_EXTENSION.getRedisCluster(),
        List.of("FOO"), EXECUTOR, Clock.systemUTC());

    manager.updateCacheIfNecessary();

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("3.50"));

    manager.updateCacheIfNecessary();

    CurrencyConversionEntityList conversions = manager.getCurrencyConversions().orElseThrow();

    assertThat(conversions.getCurrencies().size()).isEqualTo(1);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().size()).isEqualTo(4);
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("2.35"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("EUR")).isEqualTo(new BigDecimal("1.9337586"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FJD")).isEqualTo(new BigDecimal("4.835595"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FKP")).isEqualTo(new BigDecimal("1.7470981"));
  }

  @Test
  void testCurrencyCalculationsCoinGeckoTimeoutWithRun() throws IOException {
    FixerClient fixerClient = mock(FixerClient.class);
    CoinGeckoClient   CoinGeckoClient   = mock(CoinGeckoClient.class);

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("2.35"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("0.822876"),
        "FJD", new BigDecimal("2.0577"),
        "FKP", new BigDecimal("0.743446")
    ));

    CurrencyConversionManager manager = new CurrencyConversionManager(fixerClient, CoinGeckoClient, REDIS_CLUSTER_EXTENSION.getRedisCluster(),
        List.of("FOO"), EXECUTOR, Clock.systemUTC());

    manager.updateCacheIfNecessary();

    REDIS_CLUSTER_EXTENSION.getRedisCluster().useCluster(connection ->
        connection.sync().del(CurrencyConversionManager.COIN_GECKO_CAP_SHARED_CACHE_CURRENT_KEY));

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("3.50"));
    manager.updateCacheIfNecessary();

    CurrencyConversionEntityList conversions = manager.getCurrencyConversions().orElseThrow();

    assertThat(conversions.getCurrencies().size()).isEqualTo(1);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().size()).isEqualTo(4);
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("3.5"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("EUR")).isEqualTo(new BigDecimal("2.880066"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FJD")).isEqualTo(new BigDecimal("7.20195"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FKP")).isEqualTo(new BigDecimal("2.602061"));
  }


  @Test
  void testCurrencyCalculationsFixerTimeoutWithRun() throws IOException {
    FixerClient fixerClient = mock(FixerClient.class);
    CoinGeckoClient   CoinGeckoClient   = mock(CoinGeckoClient.class);

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("2.35"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("0.822876"),
        "FJD", new BigDecimal("2.0577"),
        "FKP", new BigDecimal("0.743446")
    ));

    final Instant currentTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    final Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(currentTime);
    when(clock.millis()).thenReturn(currentTime.toEpochMilli());

    CurrencyConversionManager manager = new CurrencyConversionManager(fixerClient, CoinGeckoClient, REDIS_CLUSTER_EXTENSION.getRedisCluster(),
        List.of("FOO"), EXECUTOR, clock);

    manager.updateCacheIfNecessary();

    when(CoinGeckoClient.getSpotPrice(eq("FOO"), eq("USD"))).thenReturn(new BigDecimal("3.50"));
    when(fixerClient.getConversionsForBase(eq("USD"))).thenReturn(Map.of(
        "EUR", new BigDecimal("0.922876"),
        "FJD", new BigDecimal("2.0577"),
        "FKP", new BigDecimal("0.743446")
    ));

    final Instant afterFixerExpiration = currentTime.plus(CurrencyConversionManager.FIXER_REFRESH_INTERVAL).plusMillis(1);
    when(clock.instant()).thenReturn(afterFixerExpiration);
    when(clock.millis()).thenReturn(afterFixerExpiration.toEpochMilli());

    manager.updateCacheIfNecessary();

    CurrencyConversionEntityList conversions = manager.getCurrencyConversions().orElseThrow();

    assertThat(conversions.getCurrencies().size()).isEqualTo(1);
    assertThat(conversions.getCurrencies().get(0).getBase()).isEqualTo("FOO");
    assertThat(conversions.getCurrencies().get(0).getConversions().size()).isEqualTo(4);
    assertThat(conversions.getCurrencies().get(0).getConversions().get("USD")).isEqualTo(new BigDecimal("2.35"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("EUR")).isEqualTo(new BigDecimal("2.1687586"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FJD")).isEqualTo(new BigDecimal("4.835595"));
    assertThat(conversions.getCurrencies().get(0).getConversions().get("FKP")).isEqualTo(new BigDecimal("1.7470981"));
  }

  @Test
  void convertToUsd() {
    final CurrencyConversionManager currencyConversionManager = new CurrencyConversionManager(mock(FixerClient.class),
        mock(CoinGeckoClient.class),
        mock(FaultTolerantRedisClusterClient.class),
        Collections.emptyList(),
        EXECUTOR,
        Clock.systemUTC());

    currencyConversionManager.setCachedFixerValues(Map.of("JPY", BigDecimal.valueOf(154.757008), "GBP", BigDecimal.valueOf(0.81196)));

    assertEquals(Optional.of(new BigDecimal("17.50")),
        currencyConversionManager.convertToUsd(new BigDecimal("17.50"), "USD"));

    assertEquals(Optional.of(new BigDecimal("17.50")),
        currencyConversionManager.convertToUsd(new BigDecimal("17.50"), "usd"));

    assertEquals(Optional.empty(),
        currencyConversionManager.convertToUsd(new BigDecimal("10.00"), "XYZ"));

    assertEquals(Optional.of(new BigDecimal("12.92")),
        currencyConversionManager.convertToUsd(new BigDecimal("2000"), "JPY"));

    assertEquals(Optional.of(new BigDecimal("12.32")),
        currencyConversionManager.convertToUsd(new BigDecimal("10"), "GBP"));
  }
}
