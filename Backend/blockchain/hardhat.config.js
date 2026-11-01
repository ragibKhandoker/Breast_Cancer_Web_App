require('@nomicfoundation/hardhat-toolbox')

// Local development only. The "localhost" network is `npx hardhat node`; its accounts are public test keys that
// must never hold real funds. Do not add a public mainnet here: audit proofs belong on a private or test chain.
module.exports = {
  solidity: {
    version: '0.8.24',
    settings: { optimizer: { enabled: true, runs: 200 } },
  },
  networks: {
    localhost: { url: process.env.HARDHAT_RPC_URL || 'http://127.0.0.1:8545' },
  },
}
