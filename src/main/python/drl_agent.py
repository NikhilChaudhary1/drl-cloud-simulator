import torch
import torch.nn as nn
import torch.optim as optim
import json
import os
import time
import random
import numpy as np

# --- CONFIGURATION ---
STATE_DIM = 3  # Matches your report: [CPU_Load, RAM_Load, Task_Size]
ACTION_DIM = 5 # Number of VMs (change this to match your CloudSim setup)
LEARNING_RATE = 0.001
GAMMA = 0.99
EPSILON = 1.0  # Exploration rate

# File paths for communication with Java
STATE_FILE = "state.json"
ACTION_FILE = "action.json"

# --- 1. THE DEEP Q-NETWORK (DQN) ARCHITECTURE ---
# As described in Section 3.3 of your report
class DQN(nn.Module):
    def __init__(self, input_dim, output_dim):
        super(DQN, self).__init__()
        # Input Layer -> Hidden Layer 1
        self.fc1 = nn.Linear(input_dim, 128)
        # Hidden Layer 1 -> Hidden Layer 2
        self.fc2 = nn.Linear(128, 128)
        # Hidden Layer 2 -> Output Layer (Q-values for each VM)
        self.fc3 = nn.Linear(128, output_dim)

        # Activation Function (ReLU)
        self.relu = nn.ReLU()

    def forward(self, x):
        x = self.relu(self.fc1(x))
        x = self.relu(self.fc2(x))
        return self.fc3(x)

# --- 2. AGENT INITIALIZATION ---
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
policy_net = DQN(STATE_DIM, ACTION_DIM).to(device)
optimizer = optim.Adam(policy_net.parameters(), lr=LEARNING_RATE)

print(f"Agent Initialized on {device}")
print("Waiting for CloudSim state observations...")

# --- 3. MAIN COMMUNICATION LOOP ---
def main_loop():
    while True:
        # Step A: Check if Java has sent a state
        if os.path.exists(STATE_FILE):
            try:
                # 1. Read the State from JSON
                with open(STATE_FILE, 'r') as f:
                    data = json.load(f)

                # Report mentions State = [CPU, RAM, Task]
                # We assume 'data' looks like: {"cpu_load": 0.5, "ram_load": 0.2, "task_size": 1500}
                state_vector = [
                    data.get("cpu_load", 0),
                    data.get("ram_load", 0),
                    data.get("task_size", 0)
                ]

                # Convert to Tensor for PyTorch
                state_tensor = torch.FloatTensor(state_vector).unsqueeze(0).to(device)

                # 2. Select Action (Epsilon-Greedy)
                # If random number < epsilon, choose random VM (Exploration)
                if random.random() < EPSILON:
                    action = random.randint(0, ACTION_DIM - 1)
                    print(f"State: {state_vector} | Action: VM #{action} (Random Exploration)")
                else:
                    # Else, ask the Neural Network (Exploitation)
                    with torch.no_grad():
                        q_values = policy_net(state_tensor)
                        action = q_values.argmax().item()
                        print(f"State: {state_vector} | Action: VM #{action} (AI Prediction)")

                # 3. Write Action back to JSON for Java
                response = {"vm_id": action}

                # Write to a temp file first to avoid read/write collisions
                with open("action_temp.json", 'w') as f:
                    json.dump(response, f)

                # Rename to actual file (atomic operation)
                os.replace("action_temp.json", ACTION_FILE)

                # 4. Clean up state file to signal we are done
                os.remove(STATE_FILE)

            except Exception as e:
                print(f"Error processing state: {e}")
                time.sleep(0.1)

        else:
            # Sleep briefly to save CPU while waiting for Java
            time.sleep(0.01)

if __name__ == "__main__":
    print("Running")
    main_loop()