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

package org.apache.kafka.metadata;

import org.apache.kafka.common.metadata.DelegationTokenRecord;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.apache.kafka.common.security.token.delegation.TokenInformation;
import org.apache.kafka.common.utils.SecurityUtils;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Represents the Delegation Tokens in the metadata image.
 *
 * This class is thread-safe.
 */
public final class DelegationTokenData {

    // XXX SHould this be the information in a record (as Strings) or a tokenInformation
    private TokenInformation tokenInformation;

    public static DelegationTokenData fromRecord(DelegationTokenRecord record) {
        ArrayList<KafkaPrincipal> foo = new ArrayList<KafkaPrincipal>();
        return new DelegationTokenData(TokenInformation.fromRecord(
            record.tokenId(),
            SecurityUtils.parseKafkaPrincipal(record.owner()),
            SecurityUtils.parseKafkaPrincipal(record.requester()),
            foo, // XXX record.renewers(),
            record.issueTimestamp(),
            record.maxTimestamp(),
            record.expirationTimestamp()));
    }

    public DelegationTokenData(TokenInformation tokenInformation) {
        this.tokenInformation = tokenInformation;
    }

    public TokenInformation tokenInformation() {
        return tokenInformation;
    }

    public DelegationTokenRecord toRecord() {
        // Collection<String> foo = tokenInformation.renewersAsString();
        // String[] foos = foo.toArray(new String[foo.size()]);
        //    .setRenewers(tokenInformation.renewersAsString())
        return new DelegationTokenRecord()
            .setOwner(tokenInformation.ownerAsString())
            .setRequester(tokenInformation.tokenRequesterAsString())
            .setRenewers(new ArrayList<String>(tokenInformation.renewersAsString()))
            .setIssueTimestamp(tokenInformation.issueTimestamp())
            .setMaxTimestamp(tokenInformation.maxTimestamp())
            .setExpirationTimestamp(tokenInformation.expiryTimestamp())
            .setTokenId(tokenInformation.tokenId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenInformation);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!o.getClass().equals(DelegationTokenData.class)) return false;
        DelegationTokenData other = (DelegationTokenData) o;
        return tokenInformation.equals(other.tokenInformation);
    }

    @Override
    public String toString() {
        return "DelegationTokenData" +
            "(tokenInformation=" + tokenInformation +
            ")";
    }
}
