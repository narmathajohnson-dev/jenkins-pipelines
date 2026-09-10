def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def jdkVersion = config.jdkVersion ?: 'jdk21'

    echo "Using JDK version: ${jdkVersion}"

}