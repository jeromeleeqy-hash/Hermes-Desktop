package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import org.junit.Test
import org.junit.Assert.*

class DecisionCodecTest {
    @Test fun restoresSameRequestIdAcrossSeparateProfilesWithoutMergingChoices() {
        val requests=listOf("work","personal").map { p ->
            PendingDecision(p,HermesSession("same-session","Task in $p",workspacePath="/workspace/$p",profile=p),AgentRequest("same-request","runtime-$p","same-session",AgentRequestType.CLARIFICATION,"选择需要的内容",choices=listOf(AgentRequestChoice("文档","doc","保留文档"),AgentRequestChoice("图片","image")),allowMultiple=true,allowSession=false,allowPermanent=false))
        }
        val restored=DecisionCodec.decode(DecisionCodec.encode(requests))
        assertEquals(requests,restored)
        assertEquals(2,restored.map { it.session.scopedId }.toSet().size)
        assertFalse(restored.any { it.request.allowPermanent })
    }
}
