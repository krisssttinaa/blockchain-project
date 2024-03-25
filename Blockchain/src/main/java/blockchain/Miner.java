/*package blockchain;
public class Miner {
    private String minerName;
    private double reward;

    public Miner(String minerName) {
        this.minerName = minerName;
        this.reward = 0; // Initialize reward to 0
    }

    public void mine(Blockchain blockchain) {
        int index = blockchain.getLatestBlock().getIndex() + 1;
        String previousHash = blockchain.getLatestBlock().getHash();
        Block newBlock = new Block(index, previousHash, "Block data"); // You can adjust block data as needed
        newBlock.mineBlock(4); // Mine the block with difficulty 4 (adjust as needed)
        blockchain.addBlock(newBlock);
        reward += 6.25; // Update miner's reward
        System.out.println("Block mined by " + minerName + ", Reward: " + reward);
    }

    // Getters
    public String getMinerName() {
        return minerName;
    }

    public double getReward() {
        return reward;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }
}
*/