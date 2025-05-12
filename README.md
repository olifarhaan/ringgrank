# Real-Time Global Leaderboard Service (RinggRank)

## 1.0 Introduction
This project implements a "Real-Time Global Leaderboard as a Service" for "Ringg-Play," a fictitious company hosting hyper-casual mobile games. The service processes player scores streamed from game sessions and provides live leaderboard information via a REST API.

This document outlines the architecture, features, setup, and design decisions made during the implementation based on the provided assignment.

## 2.0 Architecture Overview
The service is a Java Spring Boot application designed to operate as a single node, handling score ingestion and leaderboard queries with high throughput and low latency. The core of the service relies on an in-memory data store for real-time processing, with a persistence mechanism based on Write-Ahead Logging (WAL) and periodic snapshots to ensure data durability and fast recovery.

Please refer to the link below for more details.

[DESIGN_DOCUMENT.md](DESIGN_DOCUMENT.md)


## 3.0 Local Setup Instructions

**Prerequisites:**
* Java Development Kit (JDK) 21 or later.
* Gradle (The project includes a Gradle wrapper).
* Python 3.8+ (for running the test script).
* `pip` (Python package installer).

**Build the Project:**
1.  Clone the repository.
2.  Navigate to the project's root directory (`ringgrank`).
3.  Build the project using the Gradle wrapper:
    * On Linux/macOS: `./gradlew build`
    * On Windows: `gradlew.bat build`

**Run the Service:**
1.  After a successful build, the executable JAR will be in `build/libs/`.
2.  Run the application:
    `java -jar build/libs/ringgrank-0.0.1-SNAPSHOT.jar`
    (Replace `ringgrank-0.0.1-SNAPSHOT.jar` with the actual JAR file name if different).
3.  Alternatively, run directly using Gradle:
    * On Linux/macOS: `./gradlew bootRun`
    * On Windows: `gradlew.bat bootRun`
4.  The service will start, typically on `http://localhost:8080`. Data directories (`./data/wal`, `./data/snapshot`) will be created if they don't exist.

**Configuration:**
The application uses `src/main/resources/application.properties`. Key configurations from `GlobalLeaderboardManager.java` (via `@Value` annotations with defaults) include:
* `leaderboard.wal.path`: Path for the Write-Ahead Log file (default: `./data/wal/scores`).
* `leaderboard.snapshot.path`: Path for the snapshot file (default: `./data/snapshot/leaderboard`).
* `leaderboard.snapshot.interval`: Interval for creating snapshots in milliseconds (default: 3600000ms = 1 hour).

## 4.0 Testing Instructions

**Python `pytest` Simulation/Load Testing:**
The provided Python test script (`src/test/python/com/ringgrank/test_leaderboard_controller.py`) can be used to simulate score requests and leaderboard queries. It uses `aiohttp` for asynchronous requests.

1.  **Prerequisites:**
    * Ensure Python 3.8+ and `pip` are installed.
    * Install required Python packages:
        ```bash
        pip install pytest pytest-asyncio aiohttp
        ```

2.  **Configure Test Script:**
    * The script reads `BASE_URL` from the environment variable `LEADERBOARD_BASE_URL` (defaults to `http://localhost:8080/api/v1`).
    * Other parameters like `TARGET_GAME_ID`, `NUM_SCORE_WRITES`, etc., can be set via environment variables or modified directly in the script.

3.  **Run the Python Tests:**
    * Navigate to the directory containing the test script (e.g., `ringgrank/src/test/python/com/ringgrank/`).
    * To run the asynchronous performance simulation tests:
        ```bash
        pytest -s -v test_leaderboard_controller.py
        ```
        The `-s` flag shows print outputs. The script uses `@pytest.mark.asyncio` for its test functions.
