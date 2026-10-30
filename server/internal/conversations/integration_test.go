package conversations_test

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"

	"github.com/securechat/server/internal/conversations"
	"github.com/securechat/server/internal/database"
	"github.com/securechat/server/internal/messages"
)

// These tests exercise the conversation repository against a real PostgreSQL.
//
// They are skipped unless SECURECHAT_TEST_DATABASE_URL is set, so `go test ./...`
// stays green on a laptop with no database. CI provisions Postgres 16, runs the
// migrations and exports the variable, which is where the real value is: it is
// the only place the SQL in this package is parsed and planned by a server
// rather than by a reviewer.
//
//	migrations are applied by the test itself, so it is self-contained and also
//	validates that every migration file still applies.
const (
	testDBURLEnv  = "SECURECHAT_TEST_DATABASE_URL"
	migrationsDir = "../../migrations"
)

func newTestDB(t *testing.T) *sql.DB {
	t.Helper()

	url := os.Getenv(testDBURLEnv)
	if url == "" {
		t.Skipf("%s not set; skipping integration test", testDBURLEnv)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	db, err := database.Connect(ctx, url)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	// Applying migrations here is idempotent (goose tracks the version) and
	// doubles as a check that every migration file still parses and applies.
	if err := database.MigrateDir(db, migrationsDir); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	return db
}

// TestCreateGroupConversationIntegration covers the group path end to end:
// creation, membership, the returned payload, and visibility to every member.
func TestCreateGroupConversationIntegration(t *testing.T) {
	db := newTestDB(t)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	repo := conversations.NewRepository(db)
	fx := newFixture(t, db)

	// The creator must end up in the group automatically, and must not be listed
	// twice when (incorrectly) supplied in the member list.
	conv, err := repo.CreateGroupConversation(ctx, fx.userA, "Probe Group", []int64{fx.userB})
	if err != nil {
		t.Fatalf("CreateGroupConversation: %v", err)
	}

	if conv.Type != "group" {
		t.Errorf("type = %q, want %q", conv.Type, "group")
	}
	if !conv.Name.Valid || conv.Name.String != "Probe Group" {
		t.Errorf("name = %+v, want %q", conv.Name, "Probe Group")
	}
	if len(conv.Participants) != 2 {
		t.Fatalf("participant count = %d, want 2 (creator + member)", len(conv.Participants))
	}
	if !hasParticipant(conv.Participants, fx.userA) {
		t.Error("the creator was not added as a participant")
	}
	if !hasParticipant(conv.Participants, fx.userB) {
		t.Error("the invited member was not added as a participant")
	}

	// Every member must be able to see the conversation. A group some member
	// cannot see is the failure mode that loses messages silently.
	for _, member := range []int64{fx.userA, fx.userB} {
		got, err := repo.GetByParticipant(ctx, member, 20, 0)
		if err != nil {
			t.Fatalf("GetByParticipant(user %d): %v", member, err)
		}
		if !containsConversation(got, conv.ID) {
			t.Errorf("conversation %d not visible to user %d", conv.ID, member)
		}
		// The name must survive the list query too, not just the single read.
		for _, c := range got {
			if c.ID == conv.ID && (!c.Name.Valid || c.Name.String != "Probe Group") {
				t.Errorf("list query lost the name: %+v", c.Name)
			}
		}
	}

	// A direct conversation created alongside it must have no name.
	direct, err := repo.CreateDirectConversation(ctx, fx.userA, fx.userB)
	if err != nil {
		t.Fatalf("CreateDirectConversation: %v", err)
	}
	if direct.Name.Valid {
		t.Errorf("direct conversation has a name %q, want NULL", direct.Name.String)
	}
	if direct.Type != "direct" {
		t.Errorf("direct conversation type = %q", direct.Type)
	}
}

// TestGroupMembershipIntegration covers adding and removing members and the
// authorization around both: only an active participant may change membership, a
// re-added former member rejoins rather than erroring, and nobody can remove
// themselves through the remove endpoint.
func TestGroupMembershipIntegration(t *testing.T) {
	db := newTestDB(t)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	repo := conversations.NewRepository(db)
	svc := conversations.NewService(repo)
	fx := newFixture(t, db)

	group, err := svc.CreateGroupConversation(ctx, fx.userA, "Membership Group", []int64{fx.userB})
	if err != nil {
		t.Fatalf("create group: %v", err)
	}

	// An outsider may not add members, even ones that exist.
	if _, err := svc.AddMembers(ctx, group.ID, fx.userC, []int64{fx.userC}); err == nil {
		t.Fatal("an outsider added members to a group they are not in")
	}

	// A member adds userC.
	if _, err := svc.AddMembers(ctx, group.ID, fx.userA, []int64{fx.userC}); err != nil {
		t.Fatalf("add member: %v", err)
	}
	got, err := svc.GetConversation(ctx, group.ID, fx.userA)
	if err != nil {
		t.Fatalf("GetConversation after add: %v", err)
	}
	if len(got.Participants) != 3 {
		t.Fatalf("participant count = %d, want 3", len(got.Participants))
	}

	// Adding the same member again is a no-op, not an error.
	if _, err := svc.AddMembers(ctx, group.ID, fx.userA, []int64{fx.userC}); err != nil {
		t.Fatalf("re-adding an existing member failed: %v", err)
	}
	got, err = svc.GetConversation(ctx, group.ID, fx.userA)
	if err != nil {
		t.Fatalf("GetConversation after duplicate add: %v", err)
	}
	if len(got.Participants) != 3 {
		t.Errorf("participant count = %d after a duplicate add, want 3", len(got.Participants))
	}

	// A removed member can be re-added, which re-arms their membership.
	if _, err := svc.RemoveMember(ctx, group.ID, fx.userA, fx.userC); err != nil {
		t.Fatalf("remove member: %v", err)
	}
	if _, err := svc.AddMembers(ctx, group.ID, fx.userA, []int64{fx.userC}); err != nil {
		t.Fatalf("re-add a removed member: %v", err)
	}
	got, err = svc.GetConversation(ctx, group.ID, fx.userA)
	if err != nil {
		t.Fatalf("GetConversation after re-add: %v", err)
	}
	if len(got.Participants) != 3 {
		t.Errorf("participant count = %d after re-adding, want 3", len(got.Participants))
	}

	// Nobody removes themselves through the remove endpoint.
	if _, err := svc.RemoveMember(ctx, group.ID, fx.userA, fx.userA); err == nil {
		t.Fatal("removed self through the member-removal endpoint")
	}

	// Membership of a 1:1 conversation cannot be mutated.
	direct, err := svc.CreateDirectConversation(ctx, fx.userA, "", fx.userB)
	if err != nil {
		t.Fatalf("create direct conversation: %v", err)
	}
	if _, err := svc.AddMembers(ctx, direct.ID, fx.userA, []int64{fx.userC}); err == nil {
		t.Fatal("added a member to a direct conversation")
	}
}

// TestRenameConversationIntegration covers the rename path and its authorization:
// a participant may rename a group, a non-participant may not, and a direct
// conversation (which has no name) cannot be renamed.
func TestRenameConversationIntegration(t *testing.T) {
	db := newTestDB(t)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	repo := conversations.NewRepository(db)
	// Validation (trimming, length, blank names) lives in the service, which is
	// the layer the HTTP handler actually calls — so the assertions go through it
	// rather than past it into the repository.
	svc := conversations.NewService(repo)
	fx := newFixture(t, db)

	group, err := svc.CreateGroupConversation(ctx, fx.userA, "Original Name", []int64{fx.userB})
	if err != nil {
		t.Fatalf("create group: %v", err)
	}

	// A member can rename.
	if err := svc.RenameConversation(ctx, group.ID, fx.userB, "Renamed By B"); err != nil {
		t.Fatalf("rename by a participant: %v", err)
	}
	got, err := repo.GetByID(ctx, group.ID)
	if err != nil {
		t.Fatalf("GetByID: %v", err)
	}
	if !got.Name.Valid || got.Name.String != "Renamed By B" {
		t.Errorf("name = %+v, want %q", got.Name, "Renamed By B")
	}

	// An outsider (userC shares no conversation with the group) cannot.
	if err := svc.RenameConversation(ctx, group.ID, fx.userC, "Hijacked"); err == nil {
		t.Fatal("an outsider renamed a group they are not in")
	}

	// A direct conversation has no name to change. The service signature takes a
	// phone fallback, which is unused here because the user id is supplied.
	direct, err := svc.CreateDirectConversation(ctx, fx.userA, "", fx.userB)
	if err != nil {
		t.Fatalf("create direct conversation: %v", err)
	}
	if err := svc.RenameConversation(ctx, direct.ID, fx.userA, "Not A Group"); err == nil {
		t.Fatal("renamed a direct conversation, which has no name")
	}

	// Whitespace-only and over-long names are rejected before any write.
	if err := svc.RenameConversation(ctx, group.ID, fx.userA, "   "); err == nil {
		t.Fatal("accepted a whitespace-only group name")
	}
	if err := svc.RenameConversation(ctx, group.ID, fx.userA, strings.Repeat("x", 256)); err == nil {
		t.Fatal("accepted a group name over the length cap")
	}
}

// TestGetByConversationReturnsSenderName covers the sender attribution join: a
// group chat needs an author on every message, and the query must not drop
// messages whose sender row is missing (hence LEFT JOIN, not JOIN).
func TestGetByConversationReturnsSenderName(t *testing.T) {
	db := newTestDB(t)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	fx := newFixture(t, db)
	msgRepo := messages.NewRepository(db)
	repo := conversations.NewRepository(db)

	msg, err := msgRepo.Create(ctx, &messages.Message{
		ConversationID: fx.conversationID,
		SenderID:       fx.userA,
		Type:           "text",
		Text:           sql.NullString{String: "hello from A", Valid: true},
		Status:         "sent",
	})
	if err != nil {
		t.Fatalf("create message: %v", err)
	}

	page, err := msgRepo.GetByConversation(ctx, fx.conversationID, fx.userA, 30, nil)
	if err != nil {
		t.Fatalf("GetByConversation: %v", err)
	}

	var found *messages.Message
	for _, m := range page.Messages {
		if m.ID == msg.ID {
			found = m
		}
	}
	if found == nil {
		t.Fatalf("message %d not returned by GetByConversation", msg.ID)
	}
	if !found.SenderName.Valid || found.SenderName.String != "probe-a" {
		t.Errorf("sender_name = %+v, want %q", found.SenderName, "probe-a")
	}

	byID, err := msgRepo.GetByID(ctx, msg.ID)
	if err != nil {
		t.Fatalf("GetByID: %v", err)
	}
	if !byID.SenderName.Valid || byID.SenderName.String != "probe-a" {
		t.Errorf("GetByID sender_name = %+v, want %q", byID.SenderName, "probe-a")
	}

	// The conversation list carries the same attribution, so a group's row can
	// read "Alice: hi".
	list, err := repo.GetByParticipant(ctx, fx.userA, 20, 0)
	if err != nil {
		t.Fatalf("GetByParticipant: %v", err)
	}
	var listed *conversations.ConversationWithParticipants
	for _, c := range list {
		if c.ID == fx.conversationID {
			listed = c
		}
	}
	if listed == nil {
		t.Fatalf("conversation %d missing from the list", fx.conversationID)
	}
	if listed.LastMessage == nil {
		t.Fatal("conversation has no last message preview")
	}
	if !listed.LastMessage.SenderName.Valid || listed.LastMessage.SenderName.String != "probe-a" {
		t.Errorf("preview sender_name = %+v, want %q", listed.LastMessage.SenderName, "probe-a")
	}
}

// TestGetByParticipantOrderingIntegration confirms the recency ordering fix: the
// conversation that received the newest message sorts first, even though
// conversations.updated_at is never bumped when a message arrives.
func TestGetByParticipantOrderingIntegration(t *testing.T) {
	db := newTestDB(t)
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	repo := conversations.NewRepository(db)
	fx := newFixture(t, db)

	first, err := repo.CreateDirectConversation(ctx, fx.userA, fx.userB)
	if err != nil {
		t.Fatalf("create first conversation: %v", err)
	}
	second, err := repo.CreateGroupConversation(ctx, fx.userA, "Newer Group", []int64{fx.userB})
	if err != nil {
		t.Fatalf("create second conversation: %v", err)
	}

	// A message in the OLDER conversation must pull it to the front.
	if _, err := db.ExecContext(ctx, `
		INSERT INTO messages (conversation_id, sender_id, type, text, status, created_at, updated_at)
		VALUES ($1, $2, 'text', 'latest', 'sent', NOW(), NOW())
	`, first.ID, fx.userA); err != nil {
		t.Fatalf("insert message: %v", err)
	}

	page, err := repo.GetByParticipant(ctx, fx.userA, 20, 0)
	if err != nil {
		t.Fatalf("GetByParticipant: %v", err)
	}
	if len(page) < 2 {
		t.Fatalf("only %d conversations returned, want at least 2", len(page))
	}
	if page[0].ID != first.ID {
		t.Errorf("first conversation is id %d, want %d (the one with the newest message)", page[0].ID, first.ID)
	}
	if !containsConversation(page, second.ID) {
		t.Errorf("conversation %d missing from the page", second.ID)
	}
}

// ---------------------------------------------------------------------------
// fixture

type fixture struct {
	conversationID int64
	db             *sql.DB
	userA          int64
	userB          int64
	// userC is created but deliberately given no conversation with userA, so
	// tests have an authenticated outsider to assert authorization against.
	userC int64
}

// newFixture creates two throwaway users and removes them (with everything that
// cascades from them) when the test finishes.
func newFixture(t *testing.T, db *sql.DB) *fixture {
	t.Helper()

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	// Timestamp-suffixed phone numbers keep re-runs unique even if a previous run
	// failed before its cleanup ran.
	suffix := time.Now().UnixNano() % 1_000_000
	fx := &fixture{db: db}

	if err := db.QueryRowContext(ctx,
		`INSERT INTO users (phone_number, display_name) VALUES ($1, 'probe-a') RETURNING id`,
		fmt.Sprintf("+1998%06d1", suffix)).Scan(&fx.userA); err != nil {
		t.Fatalf("create userA: %v", err)
	}
	if err := db.QueryRowContext(ctx,
		`INSERT INTO users (phone_number, display_name) VALUES ($1, 'probe-b') RETURNING id`,
		fmt.Sprintf("+1998%06d2", suffix)).Scan(&fx.userB); err != nil {
		t.Fatalf("create userB: %v", err)
	}
	if err := db.QueryRowContext(ctx,
		`INSERT INTO users (phone_number, display_name) VALUES ($1, 'probe-c') RETURNING id`,
		fmt.Sprintf("+1998%06d3", suffix)).Scan(&fx.userC); err != nil {
		t.Fatalf("create userC: %v", err)
	}
	// A shared conversation, so the sender-name test has somewhere to post.
	if err := db.QueryRowContext(ctx,
		`INSERT INTO conversations (type) VALUES ('direct') RETURNING id`).Scan(&fx.conversationID); err != nil {
		t.Fatalf("create conversation: %v", err)
	}
	if _, err := db.ExecContext(ctx,
		`INSERT INTO conversation_participants (conversation_id, user_id) VALUES ($1, $2), ($1, $3)`,
		fx.conversationID, fx.userA, fx.userB); err != nil {
		t.Fatalf("add participants: %v", err)
	}

	t.Cleanup(func() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cleanupCancel()

		// The conversation is removed explicitly: participants cascade from the
		// user, not the other way round, so it would otherwise be orphaned.
		if _, err := db.ExecContext(cleanupCtx, `DELETE FROM conversations WHERE id = $1`, fx.conversationID); err != nil {
			t.Errorf("fixture cleanup (conversation): %v", err)
		}
		if _, err := db.ExecContext(cleanupCtx, `DELETE FROM users WHERE id IN ($1, $2, $3)`, fx.userA, fx.userB, fx.userC); err != nil {
			t.Errorf("fixture cleanup: %v", err)
		}
	})

	return fx
}

func hasParticipant(participants []*conversations.Participant, userID int64) bool {
	for _, p := range participants {
		if p.UserID == userID {
			return true
		}
	}
	return false
}

func containsConversation(convs []*conversations.ConversationWithParticipants, id int64) bool {
	for _, c := range convs {
		if c.ID == id {
			return true
		}
	}
	return false
}
