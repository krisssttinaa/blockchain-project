package old_main;

public class Miner {
    private final String minerName; //final since we will not change it, and it should not be
    private double reward;
    public Miner(String minerName) {
        this.minerName = minerName;
        this.reward = 12.5; // what reward do we want? I just put whatever
    }

    public void mine(Blockchain blockchain) {
        int index = blockchain.getLatestBlock().getIndex() + 1; //increase the index of previous block for a new block
        //System.out.println("Index in mine fun: "+index);
        String previousHash = blockchain.getLatestBlock().getHash(); //get hash of previous block
        //System.out.println("Previous hash in mine fun: "+previousHash);
        Transaction rewardMyself = new Transaction("System", this, 6.25);

        Block newBlock = new Block(index, previousHash, rewardMyself); //initialize the new block

        //we can't do this because we can't alternate the block, was done just for playing purposes
        //newBlock.addTransaction(new Transaction("System", minerName, reward));
        blockchain.addBlock(newBlock);

        // Reward the miner for successfully mining a block
        //reward+=6.25;
        rewardMyself.processTransaction();
        System.out.println("The new block was just added, the miner got the reward and see below the details");
        System.out.println("The reward was given for "+minerName+" the current amount: "+reward);
        //System.out.println();
    }

    public String getMinerName() { return minerName;}
    public double getReward() {
        return reward;
    }
    public void setReward(double newReward){ this.reward=newReward;}
}
