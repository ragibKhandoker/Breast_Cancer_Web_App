const { expect } = require('chai')
const { ethers } = require('hardhat')

// The same reference and hash the backend sends: ref = SHA-256(audit event id), hash = the event's SHA-256.
const sha256 = (text) => ethers.sha256(ethers.toUtf8Bytes(text))

describe('AuditIntegrity', () => {
  async function deploy() {
    const [owner, backend, stranger] = await ethers.getSigners()
    const contract = await (await ethers.getContractFactory('AuditIntegrity')).deploy()
    return { contract, owner, backend, stranger }
  }

  const ref = sha256('6720c1f4a1b2c3d4e5f60718')
  const hash = '0x' + 'ab'.repeat(32)
  const hour = 1793527200n // a whole hour, as the backend sends

  it('registers a proof once and verifies the original hash only', async () => {
    const { contract } = await deploy()
    await expect(contract.register(ref, hash, hour)).to.emit(contract, 'AuditRegistered')
    const [storedHash, timestamp] = await contract.getRecord(ref)
    expect(storedHash).to.equal(hash)
    expect(timestamp).to.equal(hour)
    expect(await contract.verify(ref, hash)).to.equal(true)
    expect(await contract.verify(ref, '0x' + 'cd'.repeat(32))).to.equal(false)
    expect(await contract.verify(sha256('another-event'), hash)).to.equal(false)
  })

  it('treats a repeated identical proof as a no-op and refuses a different hash', async () => {
    const { contract } = await deploy()
    await contract.register(ref, hash, hour)
    expect(await contract.register.staticCall(ref, hash, hour)).to.equal(false)
    await expect(contract.register(ref, hash, hour)).not.to.emit(contract, 'AuditRegistered')
    await expect(contract.register(ref, '0x' + 'cd'.repeat(32), hour)).to.be.revertedWithCustomError(contract, 'ConflictingHash')
    await expect(contract.register(sha256('x'), ethers.ZeroHash, hour)).to.be.revertedWithCustomError(contract, 'EmptyHash')
  })

  it('accepts proofs only from registrars the owner allowed', async () => {
    const { contract, backend, stranger } = await deploy()
    await expect(contract.connect(stranger).register(ref, hash, hour)).to.be.revertedWithCustomError(contract, 'NotRegistrar')
    await expect(contract.connect(stranger).setRegistrar(stranger.address, true)).to.be.revertedWithCustomError(contract, 'NotOwner')
    await contract.setRegistrar(backend.address, true)
    await expect(contract.connect(backend).register(ref, hash, hour)).to.emit(contract, 'AuditRegistered')
    const [, , registrar] = await contract.getRecord(ref)
    expect(registrar).to.equal(backend.address)
  })

  it('uses the function selectors the backend hard-codes through Keccak-256', async () => {
    const { contract } = await deploy()
    const iface = contract.interface
    // Backend: Keccak256.selector(...) in com.cancerweb.backend.audit
    expect(iface.getFunction('register').selector).to.equal(ethers.id('register(bytes32,bytes32,uint64)').slice(0, 10))
    expect(iface.getFunction('verify').selector).to.equal(ethers.id('verify(bytes32,bytes32)').slice(0, 10))
    expect(iface.getFunction('getRecord').selector).to.equal(ethers.id('getRecord(bytes32)').slice(0, 10))
  })
})
