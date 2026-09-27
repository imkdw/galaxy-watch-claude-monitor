.PHONY: test collector watch

test: collector watch

collector:
	cd collector && npm run check

watch:
	cd watch && ./gradlew testDebugUnitTest
