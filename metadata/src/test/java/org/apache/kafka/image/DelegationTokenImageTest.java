/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.image;

import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.token.delegation.TokenInformation;
import org.apache.kafka.common.utils.SecurityUtils;
import org.apache.kafka.image.writer.ImageWriterOptions;
import org.apache.kafka.image.writer.RecordListWriter;
import org.apache.kafka.metadata.RecordTestUtils;
import org.apache.kafka.metadata.DelegationTokenData;
import org.apache.kafka.server.common.ApiMessageAndVersion;
import org.apache.kafka.server.common.MetadataVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
// import java.util.HashMap;
import java.util.List;
// import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


@Timeout(value = 40)
public class DelegationTokenImageTest {
    public final static DelegationTokenImage IMAGE1;

    public final static List<ApiMessageAndVersion> DELTA1_RECORDS;

    final static DelegationTokenDelta DELTA1;

    final static DelegationTokenImage IMAGE2;

    static DelegationTokenData randomDelegationTokenData() {
        TokenInformation ti = new TokenInformation(
            "somerandomuuid",
            SecurityUtils.parseKafkaPrincipal("fred"),
            SecurityUtils.parseKafkaPrincipal("fred"),
            new ArrayList<KafkaPrincipal>(),
            0,
            0,
            0);
        return new DelegationTokenData(ti);
    }

    static {

//        IMAGE1 = new ScramImage(image1mechanisms);
        IMAGE1 = DelegationTokenImage.EMPTY;

        DELTA1_RECORDS = new ArrayList<>();
// Add records
        DELTA1 = new DelegationTokenDelta(IMAGE1);
        RecordTestUtils.replayAll(DELTA1, DELTA1_RECORDS);

//        IMAGE2 = new ScramImage(image2mechanisms);
        IMAGE2 = DelegationTokenImage.EMPTY;
    }

    @Test
    public void testEmptyImageRoundTrip() throws Throwable {
        testToImageAndBack(DelegationTokenImage.EMPTY);
    }

    @Test
    public void testImage1RoundTrip() throws Throwable {
        testToImageAndBack(IMAGE1);
    }

    @Test
    public void testApplyDelta1() throws Throwable {
        assertEquals(IMAGE2, DELTA1.apply());
    }

    @Test
    public void testImage2RoundTrip() throws Throwable {
        testToImageAndBack(IMAGE2);
    }

    private void testToImageAndBack(DelegationTokenImage image) throws Throwable {
        RecordListWriter writer = new RecordListWriter();
        image.write(writer, new ImageWriterOptions.Builder().build());
        DelegationTokenDelta delta = new DelegationTokenDelta(DelegationTokenImage.EMPTY);
        RecordTestUtils.replayAll(delta, writer.records());
        DelegationTokenImage nextImage = delta.apply();
        assertEquals(image, nextImage);
    }

    @Test
    public void testEmptyWithInvalidIBP() throws Throwable {
        ImageWriterOptions imageWriterOptions = new ImageWriterOptions.Builder().
                setMetadataVersion(MetadataVersion.IBP_3_5_IV2).build();
        RecordListWriter writer = new RecordListWriter();
        DelegationTokenImage.EMPTY.write(writer, imageWriterOptions);
    }

    @Test
    public void testImage1withInvalidIBP() throws Throwable {
        ImageWriterOptions imageWriterOptions = new ImageWriterOptions.Builder().
                setMetadataVersion(MetadataVersion.IBP_3_5_IV2).build();
        RecordListWriter writer = new RecordListWriter();
        try {
            IMAGE1.write(writer, imageWriterOptions);
            fail("expected exception writing IMAGE with Delegation Token records for MetadataVersion.IBP_3_5_IV2");
        } catch (Exception expected) {
            // ignore, expected
        }
    }
}