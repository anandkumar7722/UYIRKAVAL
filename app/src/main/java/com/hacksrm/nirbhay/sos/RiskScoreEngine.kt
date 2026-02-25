package com.hacksrm.nirbhay.sos

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simple singleton risk-scoring engine. Collects additive signals and
 * triggers SOSEngine when thresholds are reached.
 */
object RiskScoreEngine {
    private const val TAG = "RiskScoreEngine"
    private var score = 0

    fun addScore(value: Int, reason: String, context: Context) {
        score += value
        Log.d(TAG, "addScore +$value for $reason -> score=$score")

        // Run checks async
        CoroutineScope(Dispatchers.IO).launch {
            checkThresholds(context)
        }
    }

    fun currentScore(): Int = score

    fun resetScore() {
        score = 0
    }

    private fun checkThresholds(context: Context) {
        if (score >= 70) {
            Log.w(TAG, "Auto-triggering SOS (score=$score)")
            CoroutineScope(Dispatchers.IO).launch {
                SOSEngine.triggerSOS(TriggerSource.AUTO, context)
            }
            resetScore()
        } else if (score >= 50) {
            Log.i(TAG, "Score >=50 – silent guardian notify (simulated)")
        } else if (score >= 30) {
            Log.i(TAG, "Score >=30 – increase sensor polling (not implemented)")
        }
    }
}

