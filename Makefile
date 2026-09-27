# make test: 수집기 전체 + 워치 JVM 테스트 (계획 5장)
.PHONY: test collector watch

test: collector watch

collector:
	cd collector && npm run check

watch:
	cd watch && ./gradlew testDebugUnitTest
