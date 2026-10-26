// language: Go, file: backend/internal/api/plans.go, target: Linux (Docker), Go 1.22
package api

import "errors"

var ErrPlanNotFound = errors.New("plan not found")

// Plan is one purchasable tier. Amount is in whole rupees (integer).
type Plan struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	Amount       int    `json:"amount"`
	DurationDays int    `json:"duration_days"`
	Description  string `json:"description"`
}

// Plans is the immutable catalog. Order matters for UI display.
var Plans = []Plan{
	{
		ID:           "trial",
		Name:         "Go",
		Amount:       10,
		DurationDays: 3,
		Description:  "3 days · every platform · all qualities",
	},
	{
		ID:           "week",
		Name:         "Basic",
		Amount:       49,
		DurationDays: 7,
		Description:  "7 days · every platform · all qualities",
	},
	{
		ID:           "month",
		Name:         "Plus",
		Amount:       149,
		DurationDays: 30,
		Description:  "30 days · every platform · all qualities",
	},
	{
		ID:           "quarter",
		Name:         "Pro",
		Amount:       399,
		DurationDays: 90,
		Description:  "90 days · every platform · all qualities",
	},
}

// GetPlan returns a plan by ID or ErrPlanNotFound.
func GetPlan(id string) (*Plan, error) {
	for i := range Plans {
		if Plans[i].ID == id {
			return &Plans[i], nil
		}
	}
	return nil, ErrPlanNotFound
}
