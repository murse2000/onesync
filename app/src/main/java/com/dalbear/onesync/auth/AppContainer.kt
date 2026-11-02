package com.dalbear.onesync.auth

import android.content.Context
import com.dalbear.onesync.data.DeltaStore
import com.dalbear.onesync.data.PairRepository
import com.dalbear.onesync.graph.GraphClient
import com.dalbear.onesync.sync.SyncEngine

/**
 * 앱 전역에서 공유하는 객체.
 * MSAL 인스턴스는 프로세스당 하나만 만들면 되므로 ViewModel마다 새로 만들지 않는다.
 */
class AppContainer(context: Context) {
    val auth: OneDriveAuth = OneDriveAuth(context)
    val authStore: AuthStore = AuthStore(context)
    val graph: GraphClient = GraphClient()
    val repo: PairRepository = PairRepository(context)
    val deltaStore: DeltaStore = DeltaStore(context, graph)
    val engine: SyncEngine = SyncEngine(graph, repo, deltaStore)
}
