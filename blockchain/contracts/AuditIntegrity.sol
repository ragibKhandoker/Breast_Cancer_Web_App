// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title AuditIntegrity
/// @notice Write-once integrity proofs for BreastCare audit events. Each record is only a SHA-256 hash, a coarse
///         (hour) timestamp and an opaque reference (SHA-256 of the backend's audit event id). No names, emails,
///         images, reports, diagnoses or other health data ever reach this contract: the full events stay off-chain
///         in MongoDB, and anyone holding an event can check it here by recomputing its hash.
contract AuditIntegrity {
    struct Record {
        bytes32 hash;
        uint64 timestamp;
        address registrar;
    }

    address public immutable owner;
    mapping(address => bool) public registrars;
    mapping(bytes32 => Record) private records;

    event AuditRegistered(bytes32 indexed ref, bytes32 hash, uint64 timestamp, address indexed registrar);
    event RegistrarChanged(address indexed account, bool allowed);

    error NotOwner();
    error NotRegistrar();
    error EmptyHash();
    error ConflictingHash(bytes32 ref);

    constructor() {
        owner = msg.sender;
        registrars[msg.sender] = true;
        emit RegistrarChanged(msg.sender, true);
    }

    /// @notice Lets another backend account register proofs (or stops it).
    function setRegistrar(address account, bool allowed) external {
        if (msg.sender != owner) revert NotOwner();
        registrars[account] = allowed;
        emit RegistrarChanged(account, allowed);
    }

    /// @notice Records a proof. Records never change: registering the same hash again is a no-op (so retries and
    ///         two backend replicas are safe), and a different hash for an existing reference is refused.
    /// @return newlyRegistered false when this exact proof was already there
    function register(bytes32 ref, bytes32 hash, uint64 timestamp) external returns (bool newlyRegistered) {
        if (!registrars[msg.sender]) revert NotRegistrar();
        if (hash == bytes32(0)) revert EmptyHash();
        Record storage existing = records[ref];
        if (existing.hash != bytes32(0)) {
            if (existing.hash != hash) revert ConflictingHash(ref);
            return false;
        }
        records[ref] = Record(hash, timestamp, msg.sender);
        emit AuditRegistered(ref, hash, timestamp, msg.sender);
        return true;
    }

    /// @notice The stored proof for a reference; all zero when there is none.
    function getRecord(bytes32 ref) external view returns (bytes32 hash, uint64 timestamp, address registrar) {
        Record storage r = records[ref];
        return (r.hash, r.timestamp, r.registrar);
    }

    /// @notice True only when a proof exists for the reference and its hash equals the one given.
    function verify(bytes32 ref, bytes32 hash) external view returns (bool) {
        return hash != bytes32(0) && records[ref].hash == hash;
    }
}
