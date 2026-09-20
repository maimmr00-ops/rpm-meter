            // 3. ПРОВЕРКА ПОРОГА ГРОМКОСТИ
            // Если реальный звук тише порога — сбрасываем обороты в 0
            if (currentVolume < prefsManager.minVolumeThreshold) {
                smoothedRpm = 0f
                val engineName = when (prefsManager.engineType) {
                    2 -> "2T"
                    4 -> "4T"
                    else -> "Others"
                }
                onUpdate(0, frequency, currentVolume, "Ожидание (тихо)...")
                continue
            }
