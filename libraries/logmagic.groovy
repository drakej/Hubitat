library (
 author: "drakej",
 category: "logging",
 description: "A logging component to simplify threshold level logic",
 name: "logmagic",
 namespace: "drakej"
)

@Field static int LOG_LEVEL = 3

def logMessage(level, message) {
    if (level >= LOG_LEVEL) {
        if (level < 3) {
            log.debug message
        } else {
            log.info message
        }
    }
}