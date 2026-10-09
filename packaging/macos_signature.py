"""Verify code-page and special-slot hashes in ad-hoc Mach-O signatures.

This is an integrity check, not a replacement for macOS codesign/Gatekeeper.
Format reference: apple-oss-distributions/Security,
OSX/libsecurity_codesigning/lib/codedirectory.h (CodeDirectory).
Ad-hoc signatures have no CMS signer certificate to validate.
"""
import hashlib
import struct


def verify_adhoc(data, external_slots=None):
    external_slots = external_slots or {}
    if data[:4] in (b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"):
        count = struct.unpack_from(">I", data, 4)[0]
        wide = data[:4] == b"\xca\xfe\xba\xbf"
        result = []
        for i in range(count):
            row = 8 + i * (32 if wide else 20)
            offset, size = struct.unpack_from(">QQ" if wide else ">II", data, row + 8)
            result.extend(verify_adhoc(data[offset:offset + size], external_slots))
        assert result, "Empty universal binary"
        return result
    assert data[:4] == b"\xcf\xfa\xed\xfe", "Expected 64-bit Mach-O"
    commands = struct.unpack_from("<I", data, 16)[0]
    cursor = 32
    signature = None
    for _ in range(commands):
        command, size = struct.unpack_from("<II", data, cursor)
        assert size >= 8 and cursor + size <= len(data), "Malformed load command"
        if command == 0x1d:
            signature = struct.unpack_from("<II", data, cursor + 8)
        cursor += size
    assert signature, "Missing LC_CODE_SIGNATURE"
    offset, size = signature
    assert offset + size <= len(data)
    blob = data[offset:offset + size]
    magic, length, count = struct.unpack_from(">III", blob)
    assert magic == 0xfade0cc0 and length <= size, "Invalid embedded signature"
    children = {}
    for i in range(count):
        kind, start = struct.unpack_from(">II", blob, 12 + i * 8)
        child_length = struct.unpack_from(">I", blob, start + 4)[0]
        assert 8 <= child_length <= length - start
        children[kind] = blob[start:start + child_length]
    assert 0 in children, "Missing primary CodeDirectory"
    result = []
    for kind, cd in children.items():
        if struct.unpack_from(">I", cd)[0] != 0xfade0c02:
            continue
        version, flags, hash_offset, ident_offset, special, slots, limit = struct.unpack_from(">7I", cd, 8)
        hash_size, hash_type, _, page_bits = struct.unpack_from("4B", cd, 36)
        assert flags & 2, "Expected ad-hoc signing flag"
        if version >= 0x20100:
            assert struct.unpack_from(">I", cd, 44)[0] == 0, "Scatter signatures are not supported"
        if version >= 0x20300 and limit == 0xffffffff:
            limit = struct.unpack_from(">Q", cd, 56)[0]
        assert 0 < limit <= offset and 0 <= page_bits <= 30
        page = (1 << page_bits) if page_bits else limit
        assert slots == (limit + page - 1) // page
        algorithm = {1: "sha1", 2: "sha256", 3: "sha256", 4: "sha384"}[hash_type]
        assert hash_size == {1: 20, 2: 32, 3: 20, 4: 48}[hash_type]
        assert hash_offset >= special * hash_size and hash_offset + slots * hash_size <= len(cd)

        def digest(value):
            return hashlib.new(algorithm, value).digest()[:hash_size]

        for i in range(slots):
            stored = cd[hash_offset + i * hash_size:hash_offset + (i + 1) * hash_size]
            assert stored == digest(data[i * page:min((i + 1) * page, limit)]), "Invalid code page " + str(i)
        inputs = {k: v for k, v in children.items() if 0 < k < 0x1000}
        inputs.update(external_slots)
        for slot, value in inputs.items():
            assert slot <= special, "Missing special slot " + str(slot)
            stored = cd[hash_offset - slot * hash_size:hash_offset - (slot - 1) * hash_size]
            assert stored == digest(value), "Invalid special slot " + str(slot)
        name = cd[ident_offset:].split(b"\0", 1)[0].decode()
        result.append({"identifier": name, "code_pages": slots, "hash": algorithm,
                       "special_slots_verified": sorted(inputs), "adhoc": True})
    assert result
    return result
