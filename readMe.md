# Intelligent Cloud Resource Scheduler using Deep Reinforcement Learning (DRL)

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![PyTorch](https://img.shields.io/badge/PyTorch-EE4C2C?style=for-the-badge&logo=pytorch&logoColor=white)

An Agent-based Model for Resource Provisioning and Task Scheduling in Cloud Computing, utilizing a Deep Q-Network (DQN) to outperform traditional static scheduling heuristics like First-Come-First-Serve (FCFS) and Round Robin.

**Institution:** Graphic Era (Deemed to be University), Dehradun  
**Project Team ID:** MP2025AI0013  
**Team Members:** Nikhil Chaudhary & Kajal Jha  
**Supervisor:** Dr. Prakash Srivastava

---

## 📌 Problem Statement
Modern cloud computing environments process highly volatile and heterogeneous workloads. Conventional task scheduling algorithms (like FCFS or Round Robin) rely on static heuristics. They fail to adapt to real-time server loads or varying task sizes, leading to resource bottlenecks, inefficient utilization, higher operational costs, and poor Quality of Service (QoS) due to massive wait times.

## 🎯 Objectives
1. **Intelligent Scheduling:** Design and implement an autonomous agent using Deep Reinforcement Learning to dynamically map incoming user tasks (Cloudlets) to Virtual Machines (VMs).
2. **Latency Reduction:** Minimize the Average Task Turnaround Time (ATAT) to improve user-perceived performance.
3. **Cost Efficiency:** Reduce overall resource and execution costs for cloud providers.
4. **Cross-Language Integration:** Establish a synchronous communication bridge between a Java-based cloud simulator and a Python-based AI brain.

---

## 💡 Solution Proposed
We formulated the cloud scheduling challenge as a **Markov Decision Process (MDP)**.
* A **Java simulation engine (CloudSim Plus)** monitors the real-time load of all VMs and the size of incoming tasks.
* A **Python DRL Agent (PyTorch)** ingests this state and uses a multi-layer Neural Network (DQN) to predict the optimal VM assignment.
* The agent receives an immediate mathematical **Reward** based on whether it assigned the task to a free or bottlenecked VM, allowing it to continuously learn and optimize its policy over thousands of iterations.

## 🏗️ Architecture & Integration
The system relies on a "Stop-and-Wait" JSON bridge to pass information between the Java environment and the Python brain.

1. **State Observation (`state.json`):** Java writes the current CPU loads of all 5 VMs and the normalized size of the incoming task.
2. **Action Prediction (`action.json`):** Python reads the state, runs it through the Neural Network, and returns the ID of the selected VM.
3. **Reward Feedback (`reward.json`):** Java simulates the assignment, calculates the efficiency penalty, and sends a reward back to Python to trigger Neural Network backpropagation.

---

## 🛠️ Technologies Used
* **Simulation Engine:** Java 17+, CloudSim Plus (v8.0.0+)
* **Machine Learning:** Python 3.8+, PyTorch, NumPy
* **Communication:** JSON Serialization
* **Visualization:** Chart.js (Frontend Dashboard)

---

## 🚀 How to Run the Project

### Prerequisites
1. **Java:** Ensure JDK 17+ and Maven are installed.
2. **Python:** Ensure Python 3.8+ is installed.

### Step 1: Install Python Dependencies
Open your terminal and install the required machine learning libraries:
```bash
pip install torch numpy
```

### Step 2: Start the AI Brain (Python)
The Python script must be running first so it can listen for the Java environment. Open your terminal and run:

```bash
python src/main/python/drl_agent.py
```
Wait until the terminal outputs: Agent Active on cpu. Waiting for Java...

### Step 3: Run the Cloud Simulator (Java)
Open a new terminal (or run directly from your IDE like IntelliJ or VS Code) and execute the main Java file:

```bash
# If running via Maven/Terminal
mvn compile exec:java -Dexec.mainClass="AgentSimulation"
```
The Java engine will automatically execute 20 epochs of 500 tasks each. You will see the Average Turnaround Time (ATAT) printed in the Java console, and the metrics printed in the Python console.

---

## 📊 Results and Conclusion

The DRL Agent was stress-tested using a dynamic workload of 500 tasks against industry-standard baselines.

| Algorithm | Average Turnaround Time (ATAT) |
| :--- | :--- |
| **FCFS (Single VM Bottleneck)** | 2785.82 s |
| **Round Robin (Distributed)** | 488.07 s |
| **DRL Agent (AI Optimized)** | **468.92 s** |

**Conclusion:**
The Deep Reinforcement Learning agent successfully surpassed the Round Robin baseline. By learning to recognize busy servers and dynamically distribute high-computation tasks, the DRL agent achieved a **~4% performance gain** over traditional cyclic allocation, proving the viability of AI-driven resource provisioning in volatile cloud environments.

---

## 📂 Data & Logs
Upon running the simulation, the system automatically generates performance logs:

* `java_performance.csv`: Tracks the ATAT per training epoch.
* `training_metrics.csv`: Tracks the Neural Network's Mean Squared Error (Loss) and Exploration Rate (Epsilon).

These files can be directly imported into the `dashboard.html` interface to generate live training curves and performance comparisons.