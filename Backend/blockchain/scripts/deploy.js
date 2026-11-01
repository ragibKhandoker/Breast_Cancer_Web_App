// Deploys AuditIntegrity and prints the settings the backend needs.
//   npx hardhat node                                         (terminal 1: local chain on 127.0.0.1:8545)
//   npx hardhat run scripts/deploy.js --network localhost    (terminal 2)
// Optional: REGISTRAR=0x... also allows that account (e.g. a separate backend account) to register proofs.
const fs = require('fs')
const path = require('path')
const { ethers, network } = require('hardhat')

async function main() {
  const [deployer] = await ethers.getSigners()
  const contract = await (await ethers.getContractFactory('AuditIntegrity')).deploy()
  await contract.waitForDeployment()
  const address = await contract.getAddress()

  if (process.env.REGISTRAR) {
    await (await contract.setRegistrar(process.env.REGISTRAR, true)).wait()
  }

  const out = path.join(__dirname, '..', 'deployments', `${network.name}.json`)
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, JSON.stringify({ network: network.name, address, deployer: deployer.address, deployedAt: new Date().toISOString() }, null, 2))

  console.log(`AuditIntegrity deployed to ${address} on ${network.name}`)
  console.log('Add to Backend/.env:')
  console.log('  AUDIT_BLOCKCHAIN_ENABLED=true')
  console.log(`  AUDIT_BLOCKCHAIN_RPC_URL=${network.config.url ?? 'http://127.0.0.1:8545'}`)
  console.log(`  AUDIT_BLOCKCHAIN_ACCOUNT=${process.env.REGISTRAR ?? deployer.address}`)
  console.log(`  AUDIT_BLOCKCHAIN_CONTRACT=${address}`)
  console.log(`  AUDIT_BLOCKCHAIN_NETWORK=${network.name}`)
}

main().catch((err) => {
  console.error(err)
  process.exitCode = 1
})
