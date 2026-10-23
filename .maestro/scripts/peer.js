/**
 * 화면 밖의 사람들을 서버 호출로 움직인다. 앱에는 test1 로 로그인하고, 상대(test2)와 셋째(test3)는 여기서만 움직인다.
 *
 * ACTION 으로 할 일을 고른다. 결과는 output 에 실어 흐름이 ${output.x} 로 쓰고, 확인 결과는 output.ok 다.
 * - setup: 셋이 쓰는 새 방을 만들어 이름을 붙이고 상대가 한 마디 한다. 둘만의 방은 지난 대화가 섞여 쓰지 않는다.
 * - sendFile: setup 뒤에 상대가 그 방에 문서 파일(txt)을 올려 보낸다. 파일 이름은 output.fileName 이다.
 * - checkReply / checkEmpathy: 내가 보낸 말과 누른 공감이 서버에 남았는지
 * - checkVote / checkVoteClosed: 내 표가 들어갔는지, 투표가 마감됐는지
 * - checkLeft: 내가 방에서 나갔는지
 * - checkPolished: 내가 마지막으로 보낸 말이 다듬기 전 글(output.draftText)과 다른지
 * - cleanup: 셋 모두 방에서 나가고 이번에 받은 토큰을 지운다
 *
 * 서버 주소는 run.sh 가 `-e TALK_SERVER=` 로 넘긴다. 없으면 로컬 서버다.
 */
const BASE = typeof TALK_SERVER === 'undefined' ? 'http://localhost:8090' : TALK_SERVER
const PASSWORD = '1234'
const ME = 'test1'
const PEER = 'test2'
const THIRD = 'test3'

function call(method, path, token, body) {
    const options = { headers: { 'Content-Type': 'application/json' } }
    if (token) options.headers.Authorization = 'Bearer ' + token
    if (method !== 'GET') options.body = body === undefined ? '' : JSON.stringify(body)
    const response = method === 'GET' ? http.get(BASE + path, options)
        : method === 'PUT' ? http.put(BASE + path, options)
        : http.post(BASE + path, options)
    if (response.status >= 300) throw new Error(method + ' ' + path + ' → ' + response.status + ' ' + response.body)
    return response.body ? json(response.body) : null
}

function login(id) {
    return call('POST', '/auth/login', null, { id: id, password: PASSWORD }).token
}

function messages(token) {
    return call('GET', '/rooms/' + output.room + '/messages?limit=50', token).messages
}

function setup() {
    const tag = 'E2E-' + Date.now()
    const token = login(PEER)
    const room = call('POST', '/rooms', token, { memberIds: [ME, THIRD] })
    call('PUT', '/rooms/' + room.id + '/title', token, { title: tag })
    const said = call('POST', '/rooms/' + room.id + '/messages', token, { id: tag + '-peer', content: tag + ' hello' })
    output.peerToken = token
    output.room = room.id
    output.title = tag
    output.peerText = said.content
    output.peerMessageId = said.id
    output.replyText = tag + ' reply'
    output.voteTitle = tag + ' lunch'
    output.draftText = 'pls chek the meting time tmrw'
}

function sendFile() {
    const name = 'report-' + output.title.substring('E2E-'.length) + '.txt'
    const upload = http.post(BASE + '/rooms/' + output.room + '/files?name=' + encodeURIComponent(name), {
        headers: { 'Content-Type': 'application/octet-stream', Authorization: 'Bearer ' + output.peerToken },
        body: 'Quarterly report\nRevenue grew 12 percent. The launch moves to next month.\n',
    })
    if (upload.status >= 300) throw new Error('POST files → ' + upload.status + ' ' + upload.body)
    const file = json(upload.body)
    call('POST', '/rooms/' + output.room + '/messages', output.peerToken, {
        id: output.title + '-file',
        kind: 'file',
        payload: { fileId: file.id, fileName: file.name, fileSize: file.size },
    })
    output.fileName = file.name
}

function checkReply() {
    output.ok = messages(output.peerToken).some(m => m.senderId === ME && m.content === output.replyText)
}

function checkEmpathy() {
    const target = messages(output.peerToken).find(m => m.id === output.peerMessageId)
    output.ok = !!target && target.reactions.some(r => r.userId === ME)
}

function findVote() {
    return call('GET', '/rooms/' + output.room + '/votes', output.peerToken).votes.find(v => v.title === output.voteTitle)
}

function checkVote() {
    const vote = findVote()
    output.ok = !!vote && vote.voters.some(v => v.userId === ME)
}

function checkVoteClosed() {
    const vote = findVote()
    output.ok = !!vote && vote.isClosed
}

function checkLeft() {
    const room = call('GET', '/rooms/' + output.room, output.peerToken)
    output.ok = !room.members.some(m => m.id === ME && !m.hasLeft)
}

function checkPolished() {
    const mine = messages(output.peerToken).filter(m => m.senderId === ME && m.kind === 'text')
    const last = mine[mine.length - 1]
    output.ok = !!last && last.content.trim() !== '' && last.content !== output.draftText
}

function cleanup() {
    if (!output.room) return
    ;[ME, PEER, THIRD].forEach(id => {
        const token = id === PEER && output.peerToken ? output.peerToken : login(id)
        try { call('POST', '/rooms/' + output.room + '/leave', token) } catch (e) {}
        call('POST', '/auth/logout', token)
    })
}

const actions = { setup, sendFile, checkReply, checkEmpathy, checkVote, checkVoteClosed, checkLeft, checkPolished, cleanup }
if (!actions[ACTION]) throw new Error('모르는 ACTION: ' + ACTION)
actions[ACTION]()
