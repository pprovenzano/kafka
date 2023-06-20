/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.server

import java.nio.charset.StandardCharsets

import kafka.common.{NotificationHandler, ZkNodeChangeNotificationListener}
import kafka.zk.{DelegationTokenChangeNotificationSequenceZNode, DelegationTokenChangeNotificationZNode, DelegationTokensZNode}
import org.apache.kafka.common.security.token.delegation.internals.DelegationTokenCache
import org.apache.kafka.common.security.token.delegation.DelegationToken
import org.apache.kafka.common.utils.Time
import kafka.zk.KafkaZkClient


/*
 * Cache for Delegation Tokens when using Zk for metadata.
 * This includes other Zk specific handling of Delegation Tokens.
 */
class DelegationTokenManagerZk(config: KafkaConfig,
                               tokenCache: DelegationTokenCache,
                               time: Time,
                               val zkClient: KafkaZkClient) 
  extends DelegationTokenManager(config, tokenCache, time) {

  import DelegationTokenManager._

  private var tokenChangeListener: ZkNodeChangeNotificationListener = _

  override def startup(): Unit = {
    if (config.tokenAuthEnabled) {
      zkClient.createDelegationTokenPaths()
      loadCache()
      tokenChangeListener = new ZkNodeChangeNotificationListener(zkClient, DelegationTokenChangeNotificationZNode.path, DelegationTokenChangeNotificationSequenceZNode.SequenceNumberPrefix, TokenChangedNotificationHandler)
      tokenChangeListener.init()
    }
  }

  override def shutdown(): Unit = {
    if (config.tokenAuthEnabled) {
      if (tokenChangeListener != null) tokenChangeListener.close()
    }
  }

  private def loadCache(): Unit = {
    println("Nothing to load")
    lock.synchronized {
      val tokens = zkClient.getChildren(DelegationTokensZNode.path)
      info(s"Loading the token cache. Total token count: ${tokens.size}")
      for (tokenId <- tokens) {
        try {
          getTokenFromZk(tokenId) match {
            case Some(token) => updateCache(token)
            case None =>
          }
        } catch {
          case ex: Throwable => error(s"Error while getting Token for tokenId: $tokenId", ex)
        }
      }
    }
  }

  private def getTokenFromZk(tokenId: String): Option[DelegationToken] = {
    zkClient.getDelegationTokenInfo(tokenId) match {
      case Some(tokenInformation) => {
        val hmac = createHmac(tokenId, secretKey)
        Some(new DelegationToken(tokenInformation, hmac))
      }
      case None =>
        None
    }
  }

  /**
   * @param token
   */
  override def updateToken(token: DelegationToken): Unit = {
    zkClient.setOrCreateDelegationToken(token)
    updateCache(token)
    zkClient.createTokenChangeNotification(token.tokenInfo.tokenId())
  }

  /**
   *
   * @param tokenId
   */
  override protected def removeToken(tokenId: String): Unit = {
    zkClient.deleteDelegationToken(tokenId)
    removeCache(tokenId)
    zkClient.createTokenChangeNotification(tokenId)
  }

  /**
   *
   * @return
   */
  override def expireTokens(): Unit = {
    lock.synchronized {
      for (tokenInfo <- getAllTokenInformation) {
        val now = time.milliseconds
        if (tokenInfo.maxTimestamp < now || tokenInfo.expiryTimestamp < now) {
          info(s"Delegation token expired for token: ${tokenInfo.tokenId} for owner: ${tokenInfo.owner}")
          removeToken(tokenInfo.tokenId)
        }
      }
    }
  }

  object TokenChangedNotificationHandler extends NotificationHandler {
    println("Nothing to Notigy")
    override def processNotification(tokenIdBytes: Array[Byte]): Unit = {
      lock.synchronized {
        val tokenId = new String(tokenIdBytes, StandardCharsets.UTF_8)
        info(s"Processing Token Notification for tokenId: $tokenId")
        getTokenFromZk(tokenId) match {
          case Some(token) => updateCache(token)
          case None => removeCache(tokenId)
        }
      }
    }
  }
}

